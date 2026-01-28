package com.gengzi.rag.config;

import java.util.function.Function;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

public class MSimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {
    public static final Function<ChatClientRequest, String> DEFAULT_REQUEST_TO_STRING = ChatClientRequest::toString;
    public static final Function<ChatResponse, String> DEFAULT_RESPONSE_TO_STRING = ModelOptionsUtils::toJsonStringPrettyPrinter;
    private static final Logger logger = LoggerFactory.getLogger(MSimpleLoggerAdvisor.class);
    private final Function<ChatClientRequest, String> requestToString;
    private final Function<ChatResponse, String> responseToString;
    private final int order;

    // 用于跟踪时间指标的 ThreadLocal
    private final ThreadLocal<Long> requestStartTime = new ThreadLocal<>();
    private final ThreadLocal<Long> firstTokenTime = new ThreadLocal<>();
    private final ThreadLocal<Boolean> isFirstToken = new ThreadLocal<>();

    public MSimpleLoggerAdvisor() {
        this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, 0);
    }

    public MSimpleLoggerAdvisor(int order) {
        this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, order);
    }

    public MSimpleLoggerAdvisor(@Nullable Function<ChatClientRequest, String> requestToString,
            @Nullable Function<ChatResponse, String> responseToString, int order) {
        this.requestToString = requestToString != null ? requestToString : DEFAULT_REQUEST_TO_STRING;
        this.responseToString = responseToString != null ? responseToString : DEFAULT_RESPONSE_TO_STRING;
        this.order = order;
    }

    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        this.logRequest(chatClientRequest);
        long startTime = System.currentTimeMillis();
        requestStartTime.set(startTime);

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        long completionTime = System.currentTimeMillis() - startTime;
        this.logResponse(chatClientResponse, completionTime, null);

        requestStartTime.remove();
        return chatClientResponse;
    }

    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        this.logRequest(chatClientRequest);
        long startTime = System.currentTimeMillis();
        requestStartTime.set(startTime);
        isFirstToken.set(true);

        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

        // 跟踪首字 token 时间
        Flux<ChatClientResponse> trackedFlux = chatClientResponses.doOnNext(response -> {
            if (Boolean.TRUE.equals(isFirstToken.get())) {
                long ttft = System.currentTimeMillis() - startTime;
                firstTokenTime.set(ttft);
                isFirstToken.set(false);
            }
        });

        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(trackedFlux, response -> {
            long completionTime = System.currentTimeMillis() - startTime;
            Long ttft = firstTokenTime.get();
            this.logResponse(response, completionTime, ttft);

            // 清理 ThreadLocal
            requestStartTime.remove();
            firstTokenTime.remove();
            isFirstToken.remove();
        });
    }

    protected void logRequest(ChatClientRequest request) {
        logger.info("request: {}", JSONUtil.toJsonStr(request.prompt()));
    }

    protected void logResponse(ChatClientResponse chatClientResponse, long completionTime, Long firstTokenTimeMs) {
        // 提取 token 使用信息
        Long promptTokens = null;
        Long generationTokens = null;
        Long totalTokens = null;

        try {
            if (chatClientResponse != null && chatClientResponse.chatResponse() != null) {
                var metadata = chatClientResponse.chatResponse().getMetadata();
                if (metadata != null && metadata.getUsage() != null) {
                    var usage = metadata.getUsage();
                    // 使用安全的类型转换，因为某些 Usage 实现可能返回 Integer
                    if (usage.getPromptTokens() != null) {
                        promptTokens = usage.getPromptTokens().longValue();
                    }
                    if (usage.getCompletionTokens() != null) {
                        generationTokens = usage.getCompletionTokens().longValue();
                    }
                    if (usage.getTotalTokens() != null) {
                        totalTokens = usage.getTotalTokens().longValue();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("无法提取 token 使用信息", e);
        }

        // 格式化日志输出
        StringBuilder logMsg = new StringBuilder("响应指标");
        logMsg.append(" | 耗时: ").append(completionTime).append("ms");

        if (firstTokenTimeMs != null) {
            logMsg.append(" | TTFT: ").append(firstTokenTimeMs).append("ms");
        }

        if (promptTokens != null && generationTokens != null && totalTokens != null) {
            logMsg.append(" | Tokens: 输入=").append(promptTokens)
                    .append(", 输出=").append(generationTokens)
                    .append(", 总计=").append(totalTokens);
        }

        logger.info(logMsg.toString());
        logger.debug("response: {}", JSONUtil.toJsonStr(chatClientResponse.chatResponse()));
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public int getOrder() {
        return this.order;
    }

    public String toString() {
        return MSimpleLoggerAdvisor.class.getSimpleName();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Function<ChatClientRequest, String> requestToString;
        private Function<ChatResponse, String> responseToString;
        private int order = 0;

        private Builder() {
        }

        public Builder requestToString(Function<ChatClientRequest, String> requestToString) {
            this.requestToString = requestToString;
            return this;
        }

        public Builder responseToString(Function<ChatResponse, String> responseToString) {
            this.responseToString = responseToString;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public MSimpleLoggerAdvisor build() {
            return new MSimpleLoggerAdvisor(this.requestToString, this.responseToString, this.order);
        }
    }
}