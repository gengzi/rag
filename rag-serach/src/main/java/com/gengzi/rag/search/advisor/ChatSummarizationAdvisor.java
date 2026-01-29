package com.gengzi.rag.search.advisor;

import com.gengzi.dao.ChatSummary;
import com.gengzi.rag.search.service.ChatSummarizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 聊天摘要 Advisor
 * 1. 前处理（before）：将已有的历史摘要注入到上下文中，确保消息顺序正确
 * 2. 后处理（after）：异步触发摘要生成，不阻塞主流程
 *
 * @author: gengzi
 */
@Component
@Order(1) // 在 MessageChatMemoryAdvisor 之前执行
public class ChatSummarizationAdvisor implements BaseAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(ChatSummarizationAdvisor.class);

    @Autowired
    private ChatSummarizationService summarizationService;

    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;

    @Autowired
    @Qualifier("summaryTaskExecutor")
    private Executor summaryExecutor;

    /**
     * 前处理：注入已有摘要并排序消息
     * 消息顺序：SystemMessage → 摘要 UserMessage → 当前 UserMessage
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String conversationId = extractConversationId(request);
        if (conversationId == null) {
            return request;
        }

        try {
            // 获取已有的历史摘要
            List<ChatSummary> summaries = summarizationService.getSummaries(conversationId);

            if (summaries.isEmpty()) {
                logger.debug("会话 {} 暂无历史摘要", conversationId);
                return request;
            }

            // 构建摘要消息
            UserMessage summaryMessage = createSummaryMessage(summaries);

            // 获取当前 Prompt 的消息
            Prompt currentPrompt = request.prompt();
            List<Message> currentMessages = new ArrayList<>(currentPrompt.getInstructions());

            // 排序消息：System → 摘要 → 其他
            List<Message> sortedMessages = sortMessages(currentMessages, summaryMessage);

            // 创建新的 Prompt
            Prompt newPrompt = new Prompt(sortedMessages, currentPrompt.getOptions());

            logger.info("✅ 会话 {} 已注入 {} 个历史摘要", conversationId, summaries.size());
            logger.info("📋 消息顺序: {}",
                    sortedMessages.stream()
                            .map(m -> m.getClass().getSimpleName())
                            .collect(Collectors.joining(" → ")));

            // TODO: 需要找到正确的方式修改 request 的 prompt
            request.context().put("summaryInjected", true);

            ChatClientRequest processedChatClientRequest = request.mutate()
                    .prompt(request.prompt().mutate().messages(sortedMessages).build())
                    .build();

            return processedChatClientRequest;

        } catch (Exception e) {
            logger.error("注入摘要失败", e);
            return request;
        }
    }

    /**
     * 后处理：异步触发摘要生成
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 从 response 中获取 conversationId
        String conversationId = extractConversationId(response.context());
        if (conversationId != null) {
            triggerAsyncSummarization(conversationId);
        }
        return response;
    }

    /**
     * 排序消息：SystemMessage → 摘要 → 当前消息
     */
    private List<Message> sortMessages(List<Message> currentMessages, UserMessage summaryMessage) {
        List<Message> sorted = new ArrayList<>();

        // 1. 添加所有 SystemMessage
        currentMessages.stream()
                .filter(m -> m instanceof SystemMessage)
                .forEach(sorted::add);

        // 2. 添加摘要 UserMessage
        sorted.add(summaryMessage);

        // 3. 添加当前 UserMessage
        currentMessages.stream()
                .filter(m -> !(m instanceof SystemMessage))
                .forEach(sorted::add);

        return sorted;
    }

    /**
     * 创建摘要消息
     */
    private UserMessage createSummaryMessage(List<ChatSummary> summaries) {
        StringBuilder context = new StringBuilder();
        context.append("=== 历史对话摘要 ===\n\n");

        for (int i = 0; i < summaries.size(); i++) {
            ChatSummary summary = summaries.get(i);
            context.append(String.format("【第%d阶段】%s\n\n", i + 1, summary.getSummaryContent()));
        }

        context.append("=== 以上是历史摘要，以下是最近对话 ===\n");

        return new UserMessage(context.toString());
    }

    /**
     * 异步触发摘要生成
     */
    protected void triggerAsyncSummarization(String conversationId) {
        summaryExecutor.execute(() -> {
            try {
                // 获取当前所有消息
                List<Message> allMessages = chatMemory.get(conversationId);

                // 检查是否需要生成摘要
                if (summarizationService.shouldSummarize(conversationId, allMessages)) {
                    logger.info("🔄 后台触发摘要生成，会话ID: {}", conversationId);
                    summarizationService.generateSummaryAsync(conversationId, allMessages);
                }
            } catch (Exception e) {
                logger.error("后台摘要生成触发失败", e);
            }
        });
    }

    /**
     * 从请求中提取会话ID
     */
    private String extractConversationId(ChatClientRequest request) {
        return extractConversationId(request.context());
    }

    /**
     * 从 context 中提取会话ID
     */
    private String extractConversationId(Map<String, Object> context) {
        if (context != null && context.containsKey(ChatMemory.CONVERSATION_ID)) {
            return String.valueOf(context.get(ChatMemory.CONVERSATION_ID));
        }
        return null;
    }

    @Override
    public String getName() {
        return "ChatSummarizationAdvisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
