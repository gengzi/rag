package com.gengzi.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.gengzi.dao.Message;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.dao.repository.MessageRepository;
import com.gengzi.enums.Agent;
import com.gengzi.enums.MessageType;
import com.gengzi.rag.search.service.ChatRagService;
import com.gengzi.request.AgentChatReq;
import com.gengzi.request.ChatMsgRecordReq;
import com.gengzi.request.ChatReq;
import com.gengzi.response.BusinessException;
import com.gengzi.response.ChatMessage;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.ConversationDetailsResponse;
import com.gengzi.service.ChatService;
import com.gengzi.service.DeepResearchService;
import com.gengzi.service.PPTGenerateService;
import org.redisson.api.RMap;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    public final static String MESSAGE_MAP_KEY = "chat:hash:msg:%s";
    public final static String MESSAGE_STREAM_KEY = "chat:stream:msg:%s";
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

    private static void saveAndSend(String messageId, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink, long num, ServerSentEvent<ChatMessageResponse> chatMessageResponseServerSentEvent,
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

        serverSentEventFlux.index()
                .doOnNext(tuple -> {
                    saveAndSend(messageId, sink, tuple.getT1(), tuple.getT2(), stream, hash, lastSeq);
                })
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
                )
                .subscribe();
    }

    private void exec(ChatReq req, String userid, Sinks.Many<ServerSentEvent<ChatMessageResponse>> sink) {
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

                    // TODO 先判断是否为断点续读，前端会告知 一个 messageid  和它已经获取的 条数，比如69 需要读70 条以后得数据
                    // 根据messageid 从redis 获取数据流

                    String messageId = req.getMessageId();
                    String seqNum = req.getSeqNum();
                    if (StrUtil.isNotBlank(messageId) && StrUtil.isNotBlank(seqNum)) {
//                        Optional<Message> messageByMessageIdAndConversationId = messageRepository.findMessageByMessageIdAndConversationId(messageId, req.getConversationId());
//                        if (messageByMessageIdAndConversationId.isPresent()) {
//                            Message message = messageByMessageIdAndConversationId.get();
//                            if(){
//
//                            }
//
//                        }
                        // 获取redis 存放的chunk数据
                        // 先根据seqNum 查询redis 序号id，在循环获取redis 数据
                        RMap<String, String> map = redissonClient.getMap(String.format(MESSAGE_MAP_KEY, messageId));
                        String msgSeqId = map.get(seqNum);
                        RStream<Object, Object> stream = redissonClient.getStream(String.format(MESSAGE_STREAM_KEY, messageId));
                        StreamMessageId streamMessageId = new StreamMessageId(Long.parseLong(msgSeqId.split("-")[0]), Long.parseLong(msgSeqId.split("-")[1]) + 1);
                        while (true) {
                            Map<StreamMessageId, Map<Object, Object>> range = stream.range(10, streamMessageId, StreamMessageId.MAX);
                            for (Map.Entry<StreamMessageId, Map<Object, Object>> streamMessageIdMapEntry : range.entrySet()) {
                                Map<Object, Object> value = streamMessageIdMapEntry.getValue();
                                if (value.containsKey("message")) {
                                    String content = (String) value.get("message");
                                    ChatMessageResponse bean = JSONUtil.toBean(content, ChatMessageResponse.class);
                                    if (MessageType.END_OF_STREAM.getTypeCode().equals(bean.getMessageType())) {
                                        break;
                                    }
                                    sink.tryEmitNext(ServerSentEvent.builder(bean).build());
                                }
                            }
                        }
                    }


                    // 先判断是否是新建还是续传，
                    // 续传逻辑，直接从redis 获取数据流示返回，如果redis 数据已清空，可以降级到数据库中流示返回
                    // 新建逻辑，构建生成messageid，先插入两条记录数据，一条是用户发送的信息，一条是大模型回复的暂存信息，标记为 进行中


                    String userId = (String) securityContext.getAuthentication().getPrincipal();
                    // 3. 启动你的异步耗时任务，并把 securityContext 传进去
                    Mono.fromRunnable(() -> exec(req, userId, sink))
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();

                    return sink.asFlux()
                            .doOnCancel(() -> logger.info("SSE 流被取消"))
                            .doOnError(e -> logger.error("SSrroE 流错误", e));
                }).subscribeOn(Schedulers.boundedElastic());
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
        // TODO 需要改造，需要返回每条消息的状态，如果最新的一条消息还在进行中的，前端需要调用对话接口进行续传数据
        return Mono.fromCallable(() -> {
            if (StrUtil.isBlank(recordReq.getBefore())) {
                List<Message> messageByConversationIdAndLimit = messageRepository.findMessageByConversationIdAndLimit(conversationId, recordReq.getLimit());
                return msgListResult(messageByConversationIdAndLimit);
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
