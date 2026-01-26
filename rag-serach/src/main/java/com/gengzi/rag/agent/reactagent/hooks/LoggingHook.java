package com.gengzi.rag.agent.reactagent.hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 测试日志钩子
 */
@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class LoggingHook extends AgentHook {
    private static final Logger logger = LoggerFactory.getLogger(LoggingHook.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public String getName() {
        return "logging";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        startTime.set(System.currentTimeMillis());
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);

        logger.info("========== Agent 执行开始 ==========");
        logger.info("时间: {}", timestamp);
        // 打印配置信息
        if (config != null) {
            logger.info("配置信息: {}", config);
        }
        logger.info("=====================================");

        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        Long start = startTime.get();
        long duration = start != null ? System.currentTimeMillis() - start : 0;
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);

        logger.info("========== Agent 执行结束 ==========");
        logger.info("时间: {}", timestamp);
        logger.info("执行耗时: {} ms", duration);
        // 打印配置信息
        if (config != null) {
            logger.info("配置信息: {}", config);
        }
        logger.info("=====================================");

        startTime.remove();
        return CompletableFuture.completedFuture(Map.of());
    }
}