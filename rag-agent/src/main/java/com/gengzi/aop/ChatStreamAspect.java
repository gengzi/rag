package com.gengzi.aop;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gengzi.dao.Conversation;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.dto.RagChatMessage;
import com.gengzi.enums.ParticipantType;
import com.gengzi.request.RagChatReq;
import com.gengzi.response.AgentGraphRes;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.LlmTextRes;
import com.gengzi.response.RagReference;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Aspect
@Component
public class ChatStreamAspect {


    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;


    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;


    @Autowired
    private ConversationRepository conversationRepository;

    // 定义切点：匹配 generateStream 方法
    @Pointcut("execution(public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<com.gengzi.response.ChatAnswerResponse>> com.gengzi.service.impl.ChatServiceImpl.chatRag(com.gengzi.request.RagChatReq))")
    public void chatStreamPointcut() {
    }


    // 环绕通知：记录请求入参和响应
    @Around("chatStreamPointcut()")
    public Object aroundGenerateStream(ProceedingJoinPoint joinPoint) throws Throwable {
        // -------------------- 1. 记录请求入参 --------------------
        Object[] args = joinPoint.getArgs();
        String chatId = IdUtil.simpleUUID();
        if (args.length > 0 && args[0] instanceof RagChatReq req) {
            log.info("聊天请求入参：userId={}, query={}", req.getUserId(), req.getQuestion());
            //TODO 可根据需要存储到数据库/Redis：如保存 req 到聊天记录表

        }

        // -------------------- 2. 执行原方法，获取流式响应 --------------------
        Object result = joinPoint.proceed(); // 执行 generateStream 方法，返回 Flux<ServerSentEvent<ChatAnswerResponse>>
        if (!(result instanceof Flux)) {
            return result; // 非 Flux 类型直接返回（理论上不会进入）
        }

        Flux<ServerSentEvent<ChatAnswerResponse>> originalFlux = (Flux<ServerSentEvent<ChatAnswerResponse>>) result;

        // -------------------- 3. 收集流式响应，记录完整内容 --------------------
        List<ChatAnswerResponse> responseParts = new LinkedList<>(); // 存储响应分片
        List<ChatAnswerResponse> chatAnswerResponses = new LinkedList<>();
        // 对流式响应进行增强：收集分片 + 完成后记录完整响应
        Flux<ServerSentEvent<ChatAnswerResponse>> enhancedFlux = originalFlux
                // 1. 收集每个响应分片
                .doOnNext(sse -> {
                    ChatAnswerResponse data = sse.data();
                    if (data != null) {
                        responseParts.add(data); // 累加分片
                        log.debug("收到响应分片：{}", data.getContent()); // 可选：记录分片详情
                    }
                })
                // 2. 流完成后，汇总并记录完整响应
                .doOnComplete(() -> {
                    // 合并所有分片内容（根据实际 ChatAnswerResponse 结构调整）

                    // 相同节点内容的需要合并在一起

                    AtomicReference<ChatAnswerResponse> current = new AtomicReference<>();

                    responseParts.stream().forEach(
                            responsePart -> {
                                ChatAnswerResponse curr = current.get();

                                if (curr == null) {
                                    current.set(responsePart);
                                    return;
                                }

                                // 判断是否可以合并：必须是相同类型，并且满足内部合并条件
                                if (canMerge(curr, responsePart)) {
                                    mergeInto(curr, responsePart);
                                } else {
                                    // 不能合并：先保存当前，再开启新的
                                    chatAnswerResponses.add(curr);
                                    current.set(responsePart);
                                }
                            }
                    );

                    // 别忘了把最后一个 current 加进去！
                    if (current.get() != null) {
                        chatAnswerResponses.add(current.get());
                    }

                    // 记录完整响应（用户ID从请求入参获取，需提前保存）
                    RagChatReq req = (RagChatReq) args[0];
                    log.info("聊天响应完成：userId={}, 完整响应={}", req.getUserId(), chatAnswerResponses);
                    for (ChatAnswerResponse chatAnswerResponse : chatAnswerResponses) {
                        log.info("完整响应：{}", chatAnswerResponse.getMessageType(), chatAnswerResponse.getContent());
                    }
                    //TODO 可根据需要存储到数据库/Redis：如保存 fullContent 到聊天记录表


                    // TODO 将输出信息进行整合，加入到系统的记忆模块中(可能需要llm提取出语义信息存入记忆)
                    LinkedList<Message> messages = new LinkedList<>();
                    StringBuilder stringBuilder = new StringBuilder();
                    // llmtext 都存入记忆中作为一个信息，agent 变成一个标识信息
                    chatAnswerResponses.stream().forEach(chatAnswerResponse -> {
                        Object content = chatAnswerResponse.getContent();
                        if (content instanceof LlmTextRes llmTextRes) {
                            stringBuilder.append("\n" + llmTextRes.getAnswer() + "\n");
                        }
                        if (content instanceof AgentGraphRes agentGraphRes) {
                            String displayTitle = StrUtil.isBlank(agentGraphRes.getDisplayTitle()) ? agentGraphRes.getNodeName() : agentGraphRes.getDisplayTitle();
                            String agentContent = agentGraphRes.getContent();
                            stringBuilder.append("\n节点" + displayTitle + "执行结果:" + agentContent + "\n");
                        }

                    });
                    UserMessage userMessage = new UserMessage(req.getQuestion());
                    chatMemory.add(req.getConversationId(), List.of(userMessage));
                    messages.add(new AssistantMessage(stringBuilder.toString()));
                    chatMemory.add(req.getConversationId(), messages);
                    saveUserConversation(req.getConversationId(), chatId, req.getQuestion(), req.getSessionId());
                    saveAssistantConversation(req.getConversationId(), chatId, chatAnswerResponses);
                })
                // 3. 处理异常
                .doOnError(error -> {
                    log.error("流式响应异常：", error);
                });

        return enhancedFlux; // 返回增强后的 Flux，不影响原响应
    }


    private boolean canMerge(ChatAnswerResponse a, ChatAnswerResponse b) {
        Object contentA = a.getContent();
        Object contentB = b.getContent();

        if (contentA instanceof LlmTextRes && contentB instanceof LlmTextRes) {
            return true; // 所有 LlmTextRes 都可合并（按你的逻辑）
        }

        if (contentA instanceof AgentGraphRes agentA && contentB instanceof AgentGraphRes agentB) {
            return Objects.equals(agentA.getNodeName(), agentB.getNodeName());
        }

        return false; // 类型不同或无法合并
    }

    private void mergeInto(ChatAnswerResponse target, ChatAnswerResponse source) {
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
        }
    }


    private void saveUserConversation(String conversationId, String chatId, String question, String sessionId) {
        Optional<Conversation> conversationOptional = conversationRepository.findById(conversationId);
        if (conversationOptional.isPresent()) {
            // 存在就设置内容
            Conversation conversation = conversationOptional.get();
            String message = conversation.getMessage();
            RagChatMessage chatMessage = new RagChatMessage();
            chatMessage.setId(chatId);
            LlmTextRes llmTextRes = new LlmTextRes();
            llmTextRes.setAnswer(question);
            llmTextRes.setReference(new RagReference());
            ChatAnswerResponse chatAnswerResponse = new ChatAnswerResponse(llmTextRes, ParticipantType.TEXT.getCode(), sessionId);
            chatMessage.setContent(List.of(chatAnswerResponse));
            chatMessage.setRole(MessageType.USER.name());
            chatMessage.setConversationId(conversationId);
            chatMessage.setCreatedAt(System.currentTimeMillis());
            if (StrUtil.isNotBlank(message)) {
                List<RagChatMessage> list = JSONUtil.toList(message, RagChatMessage.class);
                list.add(chatMessage);
                conversation.setMessage(JSONUtil.toJsonStr(list));
                conversationRepository.save(conversation);
            } else {
                List<RagChatMessage> chatMessages = new ArrayList<>();
                chatMessages.add(chatMessage);
                conversation.setMessage(JSONUtil.toJsonStr(chatMessages));
                conversationRepository.save(conversation);
            }
        } else {
            throw new IllegalArgumentException("conversationId is not valid");
        }
    }


    private void saveAssistantConversation(String conversationId, String chatId, List<ChatAnswerResponse> chatAnswerResponses) {
        Optional<Conversation> conversationOptional = conversationRepository.findById(conversationId);
        if (conversationOptional.isPresent()) {
            // 存在就设置内容
            Conversation conversation = conversationOptional.get();
            String message = conversation.getMessage();
            RagChatMessage chatMessage = new RagChatMessage();
            chatMessage.setId(chatId);
            chatMessage.setContent(chatAnswerResponses);
            chatMessage.setRole(MessageType.ASSISTANT.name());
            chatMessage.setConversationId(conversationId);
            chatMessage.setCreatedAt(System.currentTimeMillis());

            List<RagChatMessage> list = JSONUtil.toList(message, RagChatMessage.class);
            list.add(chatMessage);
            conversation.setMessage(JSONUtil.toJsonStr(list));
            conversationRepository.save(conversation);

        } else {
            throw new IllegalArgumentException("conversationId is not valid");
        }
    }
}