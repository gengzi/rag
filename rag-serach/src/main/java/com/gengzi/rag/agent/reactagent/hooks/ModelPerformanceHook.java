package com.gengzi.rag.agent.reactagent.hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型性能监控 Hook
 * 用于跟踪 token 使用情况和响应时间
 */
@HookPositions({ HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL })
public class ModelPerformanceHook extends ModelHook {

    private static final Logger logger = LoggerFactory.getLogger(ModelPerformanceHook.class);

    // 使用 ThreadLocal 存储时间戳，支持并发调用
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    // 存储每次调用的开始时间（使用 config hashcode 作为 key）
    private final Map<Integer, Long> startTimeMap = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "model_performance_hook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // 记录开始时间
        long currentTime = System.currentTimeMillis();
        startTime.set(currentTime);
        startTimeMap.put(config.hashCode(), currentTime);

        // 获取输入消息数量
        Optional<Object> messagesOpt = state.value("messages");
        if (messagesOpt.isPresent()) {
            List<Message> messages = (List<Message>) messagesOpt.get();
            logger.info("准备调用模型 | 输入消息数: {}", messages.size());
            logger.debug("输入消息详情: {}", messages);
        }

        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        // 计算耗时
        Long start = startTimeMap.remove(config.hashCode());
        if (start == null) {
            start = startTime.get();
        }

        long completionTime = 0;
        if (start != null) {
            completionTime = System.currentTimeMillis() - start;
        }
        startTime.remove();

        // 构建日志信息
        StringBuilder logMsg = new StringBuilder("模型调用完成");
        logMsg.append(" | 耗时: ").append(completionTime).append("ms");

        // 尝试从 state 中获取响应消息和 token 信息
        try {
            Optional<Object> messagesOpt = state.value("messages");
            if (messagesOpt.isPresent()) {
                List<Message> messages = (List<Message>) messagesOpt.get();

                // 查找最后一条 AssistantMessage（模型的响应）
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message message = messages.get(i);
                    if (message instanceof AssistantMessage) {
                        AssistantMessage assistantMessage = (AssistantMessage) message;

                        // 尝试从 metadata 获取 token 使用信息
                        Object metadata = assistantMessage.getMetadata();
                        if (metadata != null && metadata instanceof Map) {
                            Map<String, Object> metadataMap = (Map<String, Object>) metadata;

                            // 尝试提取 usage 信息
                            Object usageObj = metadataMap.get("usage");
                            if (usageObj instanceof Usage) {
                                Usage usage = (Usage) usageObj;
                                logMsg.append(" | Tokens: 输入=").append(usage.getPromptTokens())
                                        .append(", 输出=").append(usage.getCompletionTokens())
                                        .append(", 总计=").append(usage.getTotalTokens());
                            }
                        }

                        // 记录响应长度
                        String content = assistantMessage.getText();
                        if (content != null) {
                            logMsg.append(" | 响应长度: ").append(content.length()).append(" 字符");
                        }

                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("提取 token 使用信息时出错", e);
        }

        logger.info(logMsg.toString());

        return CompletableFuture.completedFuture(Map.of());
    }
}
