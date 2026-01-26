package com.gengzi.rag.agent.reactagent.hooks;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

@HookPositions({HookPosition.BEFORE_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {
    private static final Logger logger = LoggerFactory.getLogger(MessageTrimmingHook.class);
    private static final int MAX_MESSAGES = 100;

    @Override
    public String getName() {
        return "message_trimming";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        int originalSize = previousMessages.size();

        logger.info("========== 消息裁剪 Hook 执行 ==========");
        logger.info("原始消息数量: {}", originalSize);
        logger.info("消息数量限制: {}", MAX_MESSAGES);

        // 如果消息数量超过限制，只保留最后 MAX_MESSAGES 条消息
        if (originalSize > MAX_MESSAGES) {
            int removedCount = originalSize - MAX_MESSAGES;
            List<Message> trimmedMessages = previousMessages.subList(
                    previousMessages.size() - MAX_MESSAGES,
                    previousMessages.size()
            );

            logger.warn("消息数量超过限制！已移除最早的 {} 条消息", removedCount);
            logger.info("裁剪后消息数量: {}", trimmedMessages.size());
            logger.info("保留消息范围: 第 {} 条 到 第 {} 条",
                    originalSize - MAX_MESSAGES + 1, originalSize);
            logger.info("=====================================");

            // 使用 REPLACE 策略替换所有消息
            return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
        }

        // 如果消息数量未超过限制，返回原始消息（不进行修改）
        logger.info("消息数量在限制范围内，无需裁剪");
        logger.info("=====================================");
        return new AgentCommand(previousMessages);
    }

    /**
     * 截断过长的内容用于日志输出
     */
    private String truncateContent(String content) {
        if (content == null) {
            return "null";
        }
        int maxLength = 100;
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "... (总长度: " + content.length() + " 字符)";
    }
}