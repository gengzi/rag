package com.gengzi.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.gengzi.dao.Conversation;
import com.gengzi.dao.Message;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.dao.repository.MessageRepository;
import com.gengzi.enums.Agent;
import com.gengzi.enums.ChatMessageType;
import com.gengzi.enums.MessageType;
import com.gengzi.rag.search.service.ChatRagService;
import com.gengzi.request.AgentChatReq;
import com.gengzi.request.ChatMsgRecordReq;
import com.gengzi.request.ChatReq;
import com.gengzi.request.MessageContext;
import com.gengzi.response.*;
import com.gengzi.service.ChatService;
import com.gengzi.service.DeepResearchService;
import com.gengzi.service.PPTGenerateService;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    public final static String MESSAGE_MAP_KEY = "chat:hash:msg:%s";
    public final static String MESSAGE_STREAM_KEY = "chat:stream:msg:%s";
    public final static String MESSAGE_MAP_KEY_CONVERSATION = "chat:key:conversation:%s";
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ChatRagService chatRagService;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private DeepResearchService deepResearchService;
    @Autowired
    private PPTGenerateService pptGenerateService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;

    private com.gengzi.dao.Message bulidMessage(String messageId, String messageRole, Conversation conversation, ChatMessage chatMessage) {
        com.gengzi.dao.Message messageRecord = new com.gengzi.dao.Message();
        messageRecord.setConversation(conversation.getId());
        messageRecord.setContent(JSONUtil.toJsonStr(chatMessage));
        messageRecord.setMessageRole(messageRole);
        messageRecord.setMessageId(messageId);
        messageRecord.setCreatedTime(Instant.now());
        return messageRecord;
    }

    private void saveAndSend(String messageId, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink, long num, ServerSentEvent<ChatMessageResponse> chatMessageResponseServerSentEvent,
                             RStream<String, String> stream, RMap<String, String> hash, AtomicLong lastSeq) {
        logger.info("当前流序号：{}", num);
        // 记录redis 插入数据
        ServerSentEvent<ChatMessageResponse> t2 = chatMessageResponseServerSentEvent;
        t2.data().setSeqNum(num);
        t2.data().setMessageId(messageId);
        StreamMessageId streamMessageId = stream.add(StreamAddArgs.entries(Map.of("message", JSONUtil.toJsonStr(t2.data()))));
        logger.info("插入数据：{}", streamMessageId);
        hash.put(num + "", streamMessageId.toString());
        // 更新最后序号
        lastSeq.set(num);
        sink.tryEmitNext(chatMessageResponseServerSentEvent);
    }

    private void sinksSend(ChatReq req, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink,
                           Flux<ServerSentEvent<ChatMessageResponse>> serverSentEventFlux) {

        // 获取 Stream 引用（此时 Redis 中可能还不存在）
        String messageId = req.getMessageContext().getMessageId();
        RStream<String, String> stream = redissonClient.getStream(String.format(MESSAGE_STREAM_KEY, messageId), StringCodec.INSTANCE);
        RMap<String, String> hash = redissonClient.getMap(String.format(MESSAGE_MAP_KEY, messageId), StringCodec.INSTANCE);
        // 👇 新增：用于记录最后的序号
        AtomicLong lastSeq = new AtomicLong(-1);
        List<ChatMessageResponse> responseParts = new LinkedList<>(); // 存储响应分片


        Flux<Tuple2<Long, ServerSentEvent<ChatMessageResponse>>> tuple2Flux = serverSentEventFlux
                .doOnNext(
                        sse -> {
                            ChatMessageResponse data = sse.data();
                            if (data != null) {
                                logger.info("收到响应分片：{}", data.getContent());
                                responseParts.add(data);
                            }
                        }
                )
                .index()
                .doOnNext(tuple -> {
                    saveAndSend(messageId, sink, tuple.getT1(), tuple.getT2(), stream, hash, lastSeq);
                })
                .doOnComplete(
                        () -> {
                            Mono.fromRunnable(() -> {
                                        contentMerge(responseParts, req);
                                    }).subscribeOn(Schedulers.boundedElastic())
                                    .subscribe();

                        }
                )
                .doOnComplete(() -> {
                    logger.info("Streaming completed");
                    // 发送完成信号
                    long num = lastSeq.addAndGet(1);
                    saveAndSend(messageId, sink, num, ServerSentEvent.builder(ChatMessageResponse.ofEnd()).build(), stream, hash, lastSeq);
                    sink.tryEmitComplete();
                })
                .doOnError(e ->
                        {
                            logger.error("SSrroE 流错误", e);
                            // 发送完成信号
                            long num = lastSeq.addAndGet(1);
                            saveAndSend(messageId, sink, num, ServerSentEvent.builder(ChatMessageResponse.ofEnd()).build(), stream, hash, lastSeq);
                            sink.tryEmitError(e);
                            sink.tryEmitComplete();
                        }
                );
        Mono.fromRunnable(() -> {
            // 将当前正在生成的messageid存入redis
            RBucket<String> bucket = redissonClient.getBucket(String.format(MESSAGE_MAP_KEY_CONVERSATION, req.getConversationId()));
            bucket.set(messageId); // 60秒过期
        }).thenMany(tuple2Flux).then(Mono.fromRunnable(() -> {
            // 移除
            boolean deleted = redissonClient.getBucket(String.format(MESSAGE_MAP_KEY_CONVERSATION, req.getConversationId())).delete();
            if (deleted) {
                logger.info("{}-删除正在生成的消息messageId成功", req.getConversationId());
            } else {
                logger.error("删除正在生成的消息messageId失败");
            }
        })).subscribe();
    }

    private void contentMerge(List<ChatMessageResponse> responseParts, ChatReq req) {
        List<ChatMessageResponse> chatMessageResponses = new LinkedList<>();
        // 合并所有分片内容（根据实际 ChatMessageResponse 结构调整）相同节点内容的需要合并在一起
        AtomicReference<ChatMessageResponse> current = new AtomicReference<>();
        responseParts.stream().forEach(
                responsePart -> {
                    ChatMessageResponse curr = current.get();
                    if (curr == null) {
                        current.set(responsePart);
                        return;
                    }
                    // 判断是否可以合并：必须是相同类型，并且满足内部合并条件
                    if (canMerge(curr, responsePart)) {
                        mergeInto(curr, responsePart);
                    } else {
                        // 不能合并：先保存当前，再开启新的
                        chatMessageResponses.add(curr);
                        current.set(responsePart);
                    }
                }
        );
        // 别忘了把最后一个 current 加进去！
        if (current.get() != null) {
            chatMessageResponses.add(current.get());
        }

        // 记录完整响应（用户ID从请求入参获取，需提前保存）
        logger.info("聊天响应完成：conversationId={}, 完整响应={}", req.getConversationId(), chatMessageResponses);
        for (ChatMessageResponse ChatMessageResponse : chatMessageResponses) {
            logger.info("完整响应：{}", ChatMessageResponse.getMessageType(), ChatMessageResponse.getContent());
        }

        LinkedList<org.springframework.ai.chat.messages.Message> messages = new LinkedList<>();
        StringBuilder stringBuilder = new StringBuilder();
        // llmtext 都存入记忆中作为一个信息，agent 变成一个标识信息
        chatMessageResponses.stream().forEach(ChatMessageResponse -> {
            Object content = ChatMessageResponse.getContent();
            if (content instanceof LlmTextRes llmTextRes) {
                stringBuilder.append("\n" + llmTextRes.getAnswer() + "\n");
            }
            if (content instanceof AgentGraphRes agentGraphRes) {
                String displayTitle = StrUtil.isBlank(agentGraphRes.getDisplayTitle()) ? agentGraphRes.getNodeName() : agentGraphRes.getDisplayTitle();
                String agentContent = agentGraphRes.getContent();
                stringBuilder.append("\n节点" + displayTitle + "执行结果:" + agentContent + "\n");
            }

        });
        UserMessage userMessage = new UserMessage(req.getQuery());
        // 设置聊天记忆
        chatMemory.add(req.getConversationId(), List.of(userMessage));
        messages.add(new AssistantMessage(stringBuilder.toString()));
        chatMemory.add(req.getConversationId(), messages);
        // 设置聊天记录
        String messageId = req.getMessageContext().getMessageId();
        saveAssistantConversation(req.getConversationId(), messageId, chatMessageResponses);
    }

    private boolean canMerge(ChatMessageResponse a, ChatMessageResponse b) {
        Object contentA = a.getContent();
        Object contentB = b.getContent();

        if (contentA instanceof LlmTextRes && contentB instanceof LlmTextRes) {
            return true; // 所有 LlmTextRes 都可合并（按你的逻辑）
        }

        if (contentA instanceof AgentGraphRes agentA && contentB instanceof AgentGraphRes agentB) {
            return Objects.equals(agentA.getNodeName(), agentB.getNodeName());
        }

        if (contentA instanceof WebViewRes agentA && contentB instanceof WebViewRes agentB) {
            return Objects.equals(agentA.getNodeName(), agentB.getNodeName());
        }

        return false; // 类型不同或无法合并
    }

    private void mergeInto(ChatMessageResponse target, ChatMessageResponse source) {
        Object targetContent = target.getContent();
        Object sourceContent = source.getContent();

        if (targetContent instanceof LlmTextRes targetText && sourceContent instanceof LlmTextRes sourceText) {
            targetText.setAnswer(targetText.getAnswer() + sourceText.getAnswer());
            // 关于 reference：这里假设保留 source 的（或你可以改为合并 list）
            // 如果 reference 是 List，建议改成 addAll；如果是单个，需确认业务逻辑
            if (sourceText.getReference() != null) {
                targetText.setReference(sourceText.getReference()); // 或合并逻辑
            }
        } else if (targetContent instanceof AgentGraphRes targetAgent && sourceContent instanceof AgentGraphRes sourceAgent) {
            targetAgent.setContent(targetAgent.getContent() + sourceAgent.getContent());
            // 其他字段如 nodeName 应相同（由 canMerge 保证）
        } else if (targetContent instanceof WebViewRes targetAgent && sourceContent instanceof WebViewRes sourceAgent) {
            targetAgent.setContent(targetAgent.getContent() + sourceAgent.getContent());
        }
    }

    private void saveUserConversation(String conversationId, String chatId, String question, String threadId) {
        Optional<Conversation> conversationOptional = conversationRepository.findById(conversationId);
        if (conversationOptional.isPresent()) {
            // 存在就设置内容
            Conversation conversation = conversationOptional.get();
            String message = conversation.getMessage();
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setId(chatId);
            LlmTextRes llmTextRes = new LlmTextRes();
            llmTextRes.setAnswer(question);
            llmTextRes.setReference(new RagReference());
            ChatMessageResponse ChatMessageResponse = new ChatMessageResponse(threadId, llmTextRes, ChatMessageType.LLM_RESPONSE.getTypeCode(), 0L, chatId);
            chatMessage.setContent(List.of(ChatMessageResponse));
            chatMessage.setRole(org.springframework.ai.chat.messages.MessageType.USER.name());
            chatMessage.setConversationId(conversationId);
            chatMessage.setCreatedAt(System.currentTimeMillis());
            if (StrUtil.isNotBlank(message)) {
                List<ChatMessage> list = JSONUtil.toList(message, ChatMessage.class);
                list.add(chatMessage);
                conversation.setMessage(JSONUtil.toJsonStr(list));
                com.gengzi.dao.Message messageRecord = bulidMessage(chatId, org.springframework.ai.chat.messages.MessageType.USER.name(), conversation, chatMessage);
                messageRepository.save(messageRecord);
                conversationRepository.save(conversation);
            } else {
                List<ChatMessage> chatMessages = new ArrayList<>();
                chatMessages.add(chatMessage);
                conversation.setMessage(JSONUtil.toJsonStr(chatMessages));
                com.gengzi.dao.Message messageRecord = bulidMessage(chatId, org.springframework.ai.chat.messages.MessageType.USER.name(), conversation, chatMessage);
                messageRepository.save(messageRecord);
                conversationRepository.save(conversation);
            }
        } else {
            throw new IllegalArgumentException("conversationId is not valid");
        }
    }

    private void saveAssistantConversation(String conversationId, String chatId, List<ChatMessageResponse> chatMessageResponses) {
        Optional<Conversation> conversationOptional = conversationRepository.findById(conversationId);
        if (conversationOptional.isPresent()) {
            // 存在就设置内容
            Conversation conversation = conversationOptional.get();
            String message = conversation.getMessage();
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setId(chatId);
            chatMessage.setContent(chatMessageResponses);
            chatMessage.setRole(org.springframework.ai.chat.messages.MessageType.ASSISTANT.name());
            chatMessage.setConversationId(conversationId);
            chatMessage.setCreatedAt(System.currentTimeMillis());

            List<ChatMessage> list = JSONUtil.toList(message, ChatMessage.class);
            list.add(chatMessage);
            conversation.setMessage(JSONUtil.toJsonStr(list));
            com.gengzi.dao.Message messageRecord = bulidMessage(chatId, org.springframework.ai.chat.messages.MessageType.ASSISTANT.name(), conversation, chatMessage);
            messageRepository.save(messageRecord);
            conversationRepository.save(conversation);
        } else {
            throw new IllegalArgumentException("conversationId is not valid");
        }
    }


    public void exec(ChatReq req, String userid, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {
        Flux<ServerSentEvent<ChatMessageResponse>> serverSentEventFlux;
        // 1,判断会话id是否存在,参数校验
        if (!conversationRepository.findById(req.getConversationId()).isPresent()) {
            sink.tryEmitNext(ServerSentEvent.builder(ChatMessageResponse.ofEnd()).build());
            sink.tryEmitError(new BusinessException("会话不存在"));
        }


        // 2，判断agentid是否存在，并且agentid是否可用
        if (StrUtil.isNotBlank(req.getAgentId()) && Agent.isExist(req.getAgentId())) {

            if (Agent.DEEPRESEARCH_AGENT.getCode().equals(req.getAgentId())) {
                // 3,存在agentid，执行agent流程
                AgentChatReq ragChatReq = new AgentChatReq();
                ragChatReq.setQuery(req.getQuery());
                ragChatReq.setConversationId(req.getConversationId());
                ragChatReq.setAgentId(req.getAgentId());
                ragChatReq.setUserId(userid);
                ragChatReq.setThreadId(req.getThreadId());
                serverSentEventFlux = deepResearchService.deepResearch(ragChatReq);
                sinksSend(req, sink, serverSentEventFlux);
                return;
            }

            if (Agent.PPTGENERATE_AGENT.getCode().equals(req.getAgentId())) {
                try {
                    serverSentEventFlux = pptGenerateService.pptGenerate(req);
                } catch (GraphRunnerException e) {
                    throw new RuntimeException(e);
                }
                sinksSend(req, sink, serverSentEventFlux);
                return;
            }
        }

        Flux<ChatMessageResponse> chatMessageResponseFlux = chatRagService.chatRag(req, userid);
        serverSentEventFlux = chatMessageResponseFlux.map(
                chatMessageResponse -> {
                    return ServerSentEvent.builder(chatMessageResponse).build();
                }
        );
        sinksSend(req, sink, serverSentEventFlux);
    }

    /**
     * 需要加把分布式锁，避免同一个用户访问同一个会话输出流，导致数据混乱
     * 锁key  userid+conversationId
     * <p>
     * 流内容写入到redis 中，再从redis 中订阅流
     *
     * @param req
     * @return
     */
    @Override
    public Flux<ServerSentEvent<ChatMessageResponse>> chat(ChatReq req) {
        // 1. 在 WebFlux 主链中获取当前的安全上下文（此时是有效的！）
        return ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(Mono.error(new RuntimeException("未认证")))
                .flatMapMany(securityContext -> {
                    // 2. 创建 Sinks
                    Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink =
                            Sinks.many().unicast().onBackpressureBuffer();

                    // 👇 用 AtomicReference 持有锁，供后续释放
                    AtomicReference<RLock> lockRef = new AtomicReference<>();

                    Mono<RLock> rLockMono = Mono.fromCallable(() -> {
                                RLock rLock = tryLock(req);
                                lockRef.set(rLock);
                                try {
                                    // 先存入用户的记录信息
                                    if (StrUtil.isNotBlank(req.getMessageId()) && StrUtil.isNotBlank(req.getSeqNum())) {
                                        readRedisStreams(req, sink);
                                    } else {
                                        saveUserMessage(req);
                                        String userId = (String) securityContext.getAuthentication().getPrincipal();
                                        // 3. 启动你的异步耗时任务，并把 securityContext 传进去, 只写入redis
                                        exec(req, userId, sink);
                                    }
                                } catch (Exception e) {
                                    if (rLock != null && rLock.isHeldByCurrentThread()) {
                                        rLock.unlock();
                                    }
                                    sink.tryEmitError(e);
                                }
                                return rLock;
                            })
                            .doOnError(e -> {
                                sink.tryEmitNext(ServerSentEvent.builder(ChatMessageResponse.ofRlocking()).build());
                                sink.tryEmitComplete();
                            });

                    rLockMono.subscribeOn(Schedulers.boundedElastic()).subscribe();

                    return sink.asFlux()
                            .doOnTerminate(() -> {
                                        RLock rLock = lockRef.get();
                                        if (rLock != null) {
                                            try {
                                                // ⚠️ forceUnlock 会直接删除锁，不管谁持有的！
                                                // 所以必须确保这个 key 不会被其他用户/会话误用
                                                redissonClient.getLock(rLock.getName()).forceUnlock();
                                                logger.info("强制释放锁: {}", rLock.getName());
                                            } catch (Exception e) {
                                                logger.warn("强制释放失败", e);
                                            }
                                        }
                                    }

                            )
                            .doOnCancel(() -> logger.info("SSE 流被取消"))
                            .doOnError(e -> logger.error("SSE 流错误", e));

                });
    }

    private void saveUserMessage(ChatReq req) {
        String chatId = IdUtil.simpleUUID();
        String chatResultId = IdUtil.simpleUUID();
        saveUserConversation(req.getConversationId(), chatId, req.getQuery(), req.getThreadId());
        MessageContext messageContext = new MessageContext();
        messageContext.setMessageId(chatResultId);
        req.setMessageContext(messageContext);
    }

    private RLock tryLock(ChatReq req) {
        // 手动加锁
        String lockKey = "chat:message:lock:" + req.getConversationId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean lockAcquired = false;
        // 尝试获取锁
        try {
            lockAcquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("正在对话中，请稍等！");
        }
        if (!lockAcquired) {
            throw new RuntimeException("正在对话中，请稍等！");
        }
        return lock;
    }

    private void readRedisStreams(ChatReq req, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {
        String messageId = req.getMessageId();
        String seqNum = req.getSeqNum();
        if (StrUtil.isNotBlank(messageId) && StrUtil.isNotBlank(seqNum)) {
            // 获取redis 存放的chunk数据
            // 先根据seqNum 查询redis 序号id，在循环获取redis 数据
            RMap<String, String> map = redissonClient.getMap(String.format(MESSAGE_MAP_KEY, messageId), StringCodec.INSTANCE);
            String msgSeqId = map.get(seqNum);
            RStream<Object, Object> stream = redissonClient.getStream(String.format(MESSAGE_STREAM_KEY, messageId), StringCodec.INSTANCE);
            StreamMessageId streamMessageId = new StreamMessageId(Long.parseLong(msgSeqId.split("-")[0]), Long.parseLong(msgSeqId.split("-")[1]));
            while (true) {
                if (sink.tryEmitComplete() == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                    logger.info("Sink 已取消，退出读取循环");
                    break;
                }
                boolean hasData = false;
                Map<StreamMessageId, Map<Object, Object>> range = stream.range(10, streamMessageId, StreamMessageId.MAX);
                for (Map.Entry<StreamMessageId, Map<Object, Object>> streamMessageIdMapEntry : range.entrySet()) {
                    hasData = true;
                    Map<Object, Object> value = streamMessageIdMapEntry.getValue();
                    if (value.containsKey("message")) {
                        String content = (String) value.get("message");
                        ChatMessageResponse bean = JSONUtil.toBean(content, ChatMessageResponse.class);
                        streamMessageId = new StreamMessageId(streamMessageIdMapEntry.getKey().getId0(), streamMessageIdMapEntry.getKey().getId1() + 1);
                        if (MessageType.END_OF_STREAM.getTypeCode().equals(bean.getMessageType())) {
                            sink.tryEmitNext(ServerSentEvent.builder(bean).build());
                            break;
                        }
                        sink.tryEmitNext(ServerSentEvent.builder(bean).build());
                    }
                }

                // 如果没有数据，等待一段时间再继续
                if (!hasData) {
                    Mono.delay(Duration.ofMillis(1000))
                            .doOnCancel(() -> logger.info("延迟被取消"))
                            .subscribe();
                }
            }
        }

    }


    /**
     * 获取聊天记录，分页获取
     *
     * @param conversationId
     * @param recordReq
     * @return
     */
    @Override
    public Mono<ConversationDetailsResponse> chatMsgList(String conversationId, ChatMsgRecordReq recordReq) {
        return Mono.fromCallable(() -> {
            RBucket<String> bucket = redissonClient.getBucket(String.format(MESSAGE_MAP_KEY_CONVERSATION, conversationId));
            String runMessageId = bucket.get();
            if (StrUtil.isBlank(recordReq.getBefore())) {
                List<Message> messageByConversationIdAndLimit = messageRepository.findMessageByConversationIdAndLimit(conversationId, recordReq.getLimit());
                ConversationDetailsResponse conversationDetailsResponse = msgListResult(messageByConversationIdAndLimit);
                conversationDetailsResponse.setRunMessageId(runMessageId);
                return conversationDetailsResponse;
            }
            List<String> collect = Arrays.stream(recordReq.getBefore().split("_")).collect(Collectors.toList());
            if (collect.size() != 2) {
                return new ConversationDetailsResponse();
            }
            List<Message> messageByConversationIdAndLimitAndNextCursor = messageRepository
                    .findMessageByConversationIdAndLimitAndNextCursor(conversationId, recordReq.getLimit(),
                            Instant.ofEpochMilli(Long.parseLong(collect.get(0))), Long.parseLong(collect.get(1)));
            return msgListResult(messageByConversationIdAndLimitAndNextCursor);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ConversationDetailsResponse msgListResult(List<Message> messageByConversationIdAndLimit) {
        ConversationDetailsResponse conversationDetailsResponse = new ConversationDetailsResponse();
        ArrayList<ChatMessage> chatMessages = new ArrayList<>();
        for (Message message : messageByConversationIdAndLimit) {
//            ChatMessage chatMessage = new ChatMessage();
//            chatMessage.setId(String.valueOf(message.getId()));
//            chatMessage.setRole(message.getMessageRole());
//            chatMessage.setConversationId(message.getConversation());
//            chatMessage.setCreatedAt(message.getCreatedTime().toEpochMilli());
//            chatMessage.setContent(JSONUtil.toList(message.getContent(), ChatMessageResponse.class));
//            chatMessages.add(chatMessage);
            ChatMessage bean = JSONUtil.toBean(message.getContent(), ChatMessage.class);
            chatMessages.add(bean);
        }
        List<ChatMessage> chatMessagesSort = chatMessages.stream().sorted(Comparator.comparing(ChatMessage::getCreatedAt)).collect(Collectors.toList());
        Optional<Message> messageFirst = messageByConversationIdAndLimit.stream().sorted(Comparator.comparing(Message::getId)).findFirst();
        if (messageFirst.isPresent()) {
            conversationDetailsResponse.setNextCursor(messageFirst.get().getCreatedTime().toEpochMilli() + "_" + messageFirst.get().getId());
        } else {
            conversationDetailsResponse.setNextCursor("");
        }
        conversationDetailsResponse.setMessage(chatMessagesSort);
        return conversationDetailsResponse;
    }


}
