package com.gengzi.rag.search.service;

import cn.hutool.core.util.StrUtil;
import com.gengzi.dao.ChatSummary;
import com.gengzi.dao.repository.ChatSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天摘要服务
 * 负责生成对话摘要、管理摘要存储
 *
 * @author: gengzi
 */
@Service
public class ChatSummarizationService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSummarizationService.class);

    /**
     * 摘要触发阈值：每20条消息（10轮对话）触发一次摘要
     */
    private static final int SUMMARY_THRESHOLD = 20;

    /**
     * LLM 摘要 Prompt 模板
     */
    private static final String SUMMARY_PROMPT = """
            你是一个专业的对话摘要助手。请对以下对话进行简洁摘要，保留关键信息和上下文。

            对话记录：
            {conversation}

            请生成摘要，要求：
            1. 保留用户的关键需求和问题
            2. 保留 AI 提供的重要解决方案和建议
            3. 保留关键的业务数据和事实信息
            4. 去除无关的寒暄和细节
            5. 使用第三人称叙述（如：用户询问了...，系统回答了...）
            6. 保持简洁，控制在200字以内

            摘要：
            """;

    @Autowired
    private ChatSummaryRepository chatSummaryRepository;


    @Autowired
    private ChatModel openAiChatModel;


    /**
     * 判断是否需要生成摘要
     *
     * @param conversationId 会话ID
     * @param messages       当前所有消息
     * @return 是否需要摘要
     */
    public boolean shouldSummarize(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        // 只计算用户消息和助手消息
        long count = messages.stream()
                .filter(m -> m instanceof UserMessage || m instanceof AssistantMessage)
                .count();

        // 每20条消息触发一次摘要
        boolean shouldSummarize = count > 0 && count % SUMMARY_THRESHOLD == 0;

        if (shouldSummarize) {
            // 检查是否已经为这批消息生成过摘要
            long summaryCount = chatSummaryRepository.countByConversationId(conversationId);
            long expectedSummaryCount = count / SUMMARY_THRESHOLD;

            // 如果摘要数量已经达到预期，说明已经生成过摘要了
            if (summaryCount >= expectedSummaryCount) {
                logger.debug("会话 {} 的摘要已存在，跳过生成", conversationId);
                return false;
            }
        }

        return shouldSummarize;
    }

    /**
     * 生成对话摘要
     *
     * @param conversationId 会话ID
     * @param messages       要摘要的消息列表（最近的20条）
     * @return 生成的摘要对象
     */
    public ChatSummary generateSummary(String conversationId, List<Message> messages) {
        try {
            logger.info("开始为会话 {} 生成摘要，消息数: {}", conversationId, messages.size());

            // 1. 构建对话文本
            String conversation = buildConversationText(messages);

            // 2. 调用 LLM 生成摘要
            ChatClient build = ChatClient.builder(openAiChatModel).build();
            String summaryContent = build.prompt()
                    .user(SUMMARY_PROMPT.replace("{conversation}", conversation))
                    .call()
                    .content();

            if (StrUtil.isBlank(summaryContent)) {
                logger.warn("LLM 生成的摘要为空");
                summaryContent = "自动摘要生成失败";
            }

            // 3. 创建摘要对象
            ChatSummary summary = new ChatSummary();
            summary.setConversationId(conversationId);
            summary.setSummaryContent(summaryContent.trim());

            // 设置消息ID范围（如果消息有ID的话）
            if (!messages.isEmpty()) {
                // 假设消息按时间顺序排列
                summary.setStartMessageId(getMessageId(messages.get(0), 0));
                summary.setEndMessageId(getMessageId(messages.get(messages.size() - 1), messages.size() - 1));
            } else {
                summary.setStartMessageId("unknown");
                summary.setEndMessageId("unknown");
            }

            summary.setModelName("gpt-4o-mini");
            summary.setTokenCount((long) summaryContent.length()); // 简单估算
            summary.setCreatedAt(Instant.now());
            summary.setUpdatedAt(Instant.now());

            // 4. 保存摘要
            ChatSummary saved = chatSummaryRepository.save(summary);
            logger.info("摘要生成成功，ID: {}, 内容长度: {}", saved.getId(), summaryContent.length());

            return saved;

        } catch (Exception e) {
            logger.error("生成摘要失败，会话ID: {}", conversationId, e);
            throw new RuntimeException("生成摘要失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取会话的所有历史摘要
     *
     * @param conversationId 会话ID
     * @return 摘要列表（按时间升序）
     */
    public List<ChatSummary> getSummaries(String conversationId) {
        return chatSummaryRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * 异步生成对话摘要（不阻塞主流程）
     *
     * @param conversationId 会话ID
     * @param allMessages    所有消息
     */
    @Async
    public void generateSummaryAsync(String conversationId, List<Message> allMessages) {
        try {
            logger.info("异步摘要任务开始，会话ID: {}", conversationId);

            // 获取最近的20条对话消息进行摘要
            List<Message> messagesToSummarize = allMessages.stream()
                    .filter(m -> m instanceof UserMessage || m instanceof AssistantMessage)
                    .limit(20)
                    .collect(Collectors.toList());

            if (messagesToSummarize.isEmpty()) {
                logger.warn("没有可摘要的消息");
                return;
            }

            // 调用同步方法生成摘要
            generateSummary(conversationId, messagesToSummarize);

            logger.info("异步摘要任务完成，会话ID: {}", conversationId);

        } catch (Exception e) {
            logger.error("异步摘要任务失败，会话ID: {}", conversationId, e);
            // 异步任务失败不影响主流程
        }
    }

    /**
     * 构建对话文本
     */
    private String buildConversationText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        int index = 1;
        for (Message message : messages) {
            String content = message.getText(); // 使用 getText() 方法
            if (message instanceof UserMessage) {
                sb.append(String.format("[用户 %d]: %s\n\n", index, content));
            } else if (message instanceof AssistantMessage) {
                sb.append(String.format("[助手 %d]: %s\n\n", index, content));
            }
            index++;
        }

        return sb.toString();
    }

    /**
     * 获取消息ID，如果没有则生成一个
     */
    private String getMessageId(Message message, int index) {
        // 尝试从消息元数据中获取ID
        Map<String, Object> metadata = message.getMetadata();
        if (metadata != null && metadata.containsKey("id")) {
            return String.valueOf(metadata.get("id"));
        }

        // 如果没有ID，使用内容的hash + 索引
        String content = message.getText();
        return String.valueOf(content.hashCode()) + "_" + index;
    }
}
