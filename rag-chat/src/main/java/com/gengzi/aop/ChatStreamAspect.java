package com.gengzi.aop;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gengzi.dao.Conversation;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.dao.repository.MessageRepository;
import com.gengzi.enums.ChatMessageType;
import com.gengzi.request.ChatReq;
import com.gengzi.request.MessageContext;
import com.gengzi.response.*;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 拦截聊天主入口，将用户聊天记录存储到数据库中
 * 还有用户记忆的存入
 * 支持文本和agent执行
 */
@Slf4j
@Aspect
@Component
public class ChatStreamAspect {

    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private static com.gengzi.dao.Message bulidMessage(String messageId, String messageRole, Conversation conversation, ChatMessage chatMessage) {
        com.gengzi.dao.Message messageRecord = new com.gengzi.dao.Message();
        messageRecord.setConversation(conversation.getId());
        messageRecord.setContent(JSONUtil.toJsonStr(chatMessage));
        messageRecord.setMessageRole(messageRole);
        messageRecord.setMessageId(messageId);
        messageRecord.setCreatedTime(Instant.now());
        return messageRecord;
    }

    // 定义切点：匹配 generateStream 方法
    @Pointcut("execution(public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<com.gengzi.response.ChatMessageResponse>> com.gengzi.service.impl.ChatServiceImpl.chat(com.gengzi.request.ChatReq))")
    public void chatStreamPointcut() {
    }

    // 环绕通知：记录请求入参和响应
    @Around("chatStreamPointcut()")
    public Object aroundGenerateStream(ProceedingJoinPoint joinPoint) throws Throwable {
        // -------------------- 1. 记录请求入参 --------------------
//        Object[] args = joinPoint.getArgs();
//        String chatId = IdUtil.simpleUUID();
//        String chatResultId = IdUtil.simpleUUID();
//        if (args.length > 0 && args[0] instanceof ChatReq req) {
//            log.debug("聊天请求入参：conversationId={}, query={}", req.getConversationId(), req.getQuery());
//            // 先生成消息id （messageid）用于标识某次对话的消息，只插入记录表，记录用户消息，ai回复进行占位（不占位是不是也行？？），不插入记忆表
//            saveUserConversation(req.getConversationId(), chatId, req.getQuery(), req.getThreadId());
//            MessageContext messageContext = new MessageContext();
//            messageContext.setMessageId(chatResultId);
//            req.setMessageContext(messageContext);
//        }

        // -------------------- 2. 执行原方法，获取流式响应 --------------------
        Object result = null;
        try {
            result = joinPoint.proceed(); // 执行 generateStream 方法，返回 Flux<ServerSentEvent<ChatMessageResponse>>
            if (!(result instanceof Flux)) {
                return result; // 非 Flux 类型直接返回（理论上不会进入）
            }
        } catch (Exception e) {
            log.error("请求异常：{}", e.getMessage());
        }

//
//        Flux<ServerSentEvent<ChatMessageResponse>> originalFlux = (Flux<ServerSentEvent<ChatMessageResponse>>) result;
//
//        // -------------------- 3. 收集流式响应，记录完整内容 --------------------
//        List<ChatMessageResponse> responseParts = new LinkedList<>(); // 存储响应分片
//        List<ChatMessageResponse> chatMessageResponses = new LinkedList<>();
//        // 对流式响应进行增强：收集分片 + 完成后记录完整响应
//        Flux<ServerSentEvent<ChatMessageResponse>> enhancedFlux = originalFlux
//                // 1. 收集每个响应分片
//                .doOnNext(sse -> {
//                    ChatMessageResponse data = sse.data();
//                    if (data != null) {
//                        responseParts.add(data); // 累加分片
//                        log.debug("收到响应分片：{}", data.getContent()); // 可选：记录分片详情
//                    }
//                })
//                // 2. 流完成后，汇总并记录完整响应
//                .doOnComplete(() -> {
//                    // 合并所有分片内容（根据实际 ChatMessageResponse 结构调整）
//
//                    // 相同节点内容的需要合并在一起
//
//                    AtomicReference<ChatMessageResponse> current = new AtomicReference<>();
//
//                    responseParts.stream().forEach(
//                            responsePart -> {
//                                ChatMessageResponse curr = current.get();
//
//                                if (curr == null) {
//                                    current.set(responsePart);
//                                    return;
//                                }
//
//                                // 判断是否可以合并：必须是相同类型，并且满足内部合并条件
//                                if (canMerge(curr, responsePart)) {
//                                    mergeInto(curr, responsePart);
//                                } else {
//                                    // 不能合并：先保存当前，再开启新的
//                                    chatMessageResponses.add(curr);
//                                    current.set(responsePart);
//                                }
//                            }
//                    );
//
//                    // 别忘了把最后一个 current 加进去！
//                    if (current.get() != null) {
//                        chatMessageResponses.add(current.get());
//                    }
//
//                    // 记录完整响应（用户ID从请求入参获取，需提前保存）
//                    ChatReq req = (ChatReq) args[0];
//                    log.info("聊天响应完成：conversationId={}, 完整响应={}", req.getConversationId(), chatMessageResponses);
//                    for (ChatMessageResponse ChatMessageResponse : chatMessageResponses) {
//                        log.info("完整响应：{}", ChatMessageResponse.getMessageType(), ChatMessageResponse.getContent());
//                    }
//                    //TODO 可根据需要存储到数据库/Redis：如保存 fullContent 到聊天记录表
//
//
//                    // TODO 将输出信息进行整合，加入到系统的记忆模块中(可能需要llm提取出语义信息存入记忆)
//                    LinkedList<Message> messages = new LinkedList<>();
//                    StringBuilder stringBuilder = new StringBuilder();
//                    // llmtext 都存入记忆中作为一个信息，agent 变成一个标识信息
//                    chatMessageResponses.stream().forEach(ChatMessageResponse -> {
//                        Object content = ChatMessageResponse.getContent();
//                        if (content instanceof LlmTextRes llmTextRes) {
//                            stringBuilder.append("\n" + llmTextRes.getAnswer() + "\n");
//                        }
//                        if (content instanceof AgentGraphRes agentGraphRes) {
//                            String displayTitle = StrUtil.isBlank(agentGraphRes.getDisplayTitle()) ? agentGraphRes.getNodeName() : agentGraphRes.getDisplayTitle();
//                            String agentContent = agentGraphRes.getContent();
//                            stringBuilder.append("\n节点" + displayTitle + "执行结果:" + agentContent + "\n");
//                        }
//
//                    });
//                    UserMessage userMessage = new UserMessage(req.getQuery());
//                    // 设置聊天记忆
//                    chatMemory.add(req.getConversationId(), List.of(userMessage));
//                    messages.add(new AssistantMessage(stringBuilder.toString()));
//                    chatMemory.add(req.getConversationId(), messages);
//
//                    // 设置聊天记录
//
//                    saveAssistantConversation(req.getConversationId(), chatResultId, chatMessageResponses);
//                })
//                // 3. 处理异常
//                .doOnError(error -> {
//                    log.error("流式响应异常：", error);
//                });

        return result; // 返回增强后的 Flux，不影响原响应
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
            chatMessage.setRole(MessageType.USER.name());
            chatMessage.setConversationId(conversationId);
            chatMessage.setCreatedAt(System.currentTimeMillis());
            if (StrUtil.isNotBlank(message)) {
                List<ChatMessage> list = JSONUtil.toList(message, ChatMessage.class);
                list.add(chatMessage);
                conversation.setMessage(JSONUtil.toJsonStr(list));
                com.gengzi.dao.Message messageRecord = bulidMessage(chatId, MessageType.USER.name(), conversation, chatMessage);
                messageRepository.save(messageRecord);
                conversationRepository.save(conversation);
            } else {
                List<ChatMessage> chatMessages = new ArrayList<>();
                chatMessages.add(chatMessage);
                conversation.setMessage(JSONUtil.toJsonStr(chatMessages));
                com.gengzi.dao.Message messageRecord = bulidMessage(chatId, MessageType.USER.name(), conversation, chatMessage);
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
            chatMessage.setRole(MessageType.ASSISTANT.name());
            chatMessage.setConversationId(conversationId);
            chatMessage.setCreatedAt(System.currentTimeMillis());

            List<ChatMessage> list = JSONUtil.toList(message, ChatMessage.class);
            list.add(chatMessage);
            conversation.setMessage(JSONUtil.toJsonStr(list));
            com.gengzi.dao.Message messageRecord = bulidMessage(chatId, MessageType.ASSISTANT.name(), conversation, chatMessage);
            messageRepository.save(messageRecord);
            conversationRepository.save(conversation);
        } else {
            throw new IllegalArgumentException("conversationId is not valid");
        }
    }
}