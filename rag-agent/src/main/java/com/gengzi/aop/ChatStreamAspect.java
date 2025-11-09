package com.gengzi.aop;

import com.gengzi.request.RagChatReq;
import com.gengzi.response.AgentGraphRes;
import com.gengzi.response.ChatAnswerResponse;
import com.gengzi.response.LlmTextRes;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Aspect
@Component
public class ChatStreamAspect {


    // 定义切点：匹配 generateStream 方法
    @Pointcut("execution(public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<com.gengzi.response.ChatAnswerResponse>> com.gengzi.service.impl.ChatServiceImpl.chatRag(com.gengzi.request.RagChatReq))")
    public void chatStreamPointcut() {
    }

    // 环绕通知：记录请求入参和响应
    @Around("chatStreamPointcut()")
    public Object aroundGenerateStream(ProceedingJoinPoint joinPoint) throws Throwable {
        // -------------------- 1. 记录请求入参 --------------------
        Object[] args = joinPoint.getArgs();
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
                                if (current.get() == null) {
                                    current.set(responsePart);
                                    return;
                                }
                                Object content = responsePart.getContent();
                                if (content instanceof LlmTextRes llmTextRes) {
                                    if (current.get().getContent() instanceof LlmTextRes currentLlmTextRes) {
                                        currentLlmTextRes.setAnswer(currentLlmTextRes.getAnswer() + llmTextRes.getAnswer());
                                        currentLlmTextRes.setReference(llmTextRes.getReference());
                                    } else {
                                        // 不同节点，直接存储
                                        chatAnswerResponses.add(current.get());
                                        current.set(responsePart);
                                    }

                                }
                                if (content instanceof AgentGraphRes agentGraphRes) {
                                    if (current.get().getContent() instanceof AgentGraphRes currentAgentGraphRes) {
                                        if (agentGraphRes.getNodeName().equals(currentAgentGraphRes.getNodeName())) {
                                            // 同一节点合并内容
                                            currentAgentGraphRes.setContent(currentAgentGraphRes.getContent() + agentGraphRes.getContent());
                                        } else {
                                            // 不同节点，直接存储
                                            chatAnswerResponses.add(current.get());
                                            current.set(responsePart);
                                        }
                                    } else {
                                        // 节点不同，直接存储
                                        chatAnswerResponses.add(current.get());
                                        current.set(responsePart);
                                    }
                                }
                            }
                    );

                    // 记录完整响应（用户ID从请求入参获取，需提前保存）
                    RagChatReq req = (RagChatReq) args[0];
                    log.info("聊天响应完成：userId={}, 完整响应={}", req.getUserId(), chatAnswerResponses);
                    for (ChatAnswerResponse chatAnswerResponse : chatAnswerResponses) {
                        log.info("完整响应：{}", chatAnswerResponse.getMessageType(), chatAnswerResponse.getContent());
                    }
                    //TODO 可根据需要存储到数据库/Redis：如保存 fullContent 到聊天记录表
                })
                // 3. 处理异常
                .doOnError(error -> {
                    log.error("流式响应异常：", error);
                });

        return enhancedFlux; // 返回增强后的 Flux，不影响原响应
    }
}