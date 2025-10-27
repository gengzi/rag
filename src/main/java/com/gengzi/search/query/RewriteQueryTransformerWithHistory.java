/*
 * 改造后的RewriteQueryTransformer，支持携带历史对话记录
 */
package com.gengzi.search.query;

import com.gengzi.tool.DateTimeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

public class RewriteQueryTransformerWithHistory implements QueryTransformer {

    private static final Logger logger = LoggerFactory.getLogger(RewriteQueryTransformerWithHistory.class);

    // 新增{chatHistory}占位符，让大模型参考历史对话
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            基于历史对话记录和当前查询，为{target}检索优化重写查询。
            要求：1. 结合历史对话理解用户真实需求；
            2. 删除无关信息，保持简洁具体；
            3. 若当前查询依赖历史内容，需补充完整关键信息。
            只返回重写后的查询，无需补充重写原因

            历史对话记录：
            {chatHistory}

            当前原始查询：
            {query}

            重写后的查询：
            """);

    private static final String DEFAULT_TARGET = "vector store";
    // 历史对话在Query metadata中的存储键
    private static final String CHAT_HISTORY_METADATA_KEY = "chatHistory";

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final String targetSearchSystem;

    // 构造器逻辑不变，仅提示词模板默认值更新
    public RewriteQueryTransformerWithHistory(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate,
                                              @Nullable String targetSearchSystem) {
        Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");

        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.targetSearchSystem = targetSearchSystem != null ? targetSearchSystem : DEFAULT_TARGET;

        // 新增{chatHistory}占位符校验
        PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "target", "query", "chatHistory");
    }

    @Override
    public Query transform(Query query) {
        Assert.notNull(query, "query cannot be null");
        logger.debug("结合历史对话，为重写查询以适配{}", this.targetSearchSystem);

        // 1. 从Query的metadata中获取历史对话记录（若不存在则为空列表）
        List<Message> chatHistory = query.history();
        // 2. 格式化历史对话：转为“角色：内容”的字符串，便于大模型解析
        String formattedHistory = formatChatHistory(chatHistory);

        // 3. 填充提示词模板：新增{chatHistory}参数
        var rewrittenQueryText = this.chatClient.prompt()
                .user(user -> user.text(this.promptTemplate.getTemplate())
                        .param("target", this.targetSearchSystem)
                        .param("query", query.text())
                        .param("chatHistory", formattedHistory)) // 传入格式化后的历史记录
                .call()
                .content();

        if (!StringUtils.hasText(rewrittenQueryText)) {
            logger.warn("查询重写结果为空，返回原始查询");
            return query;
        }

        // 4. 返回新Query时，保留原始历史对话 metadata（便于后续多轮使用）
        return query.mutate()
                .text(rewrittenQueryText)
                .build();
    }

    /**
     * 格式化历史对话记录：用户消息前缀“用户：”，系统消息前缀“系统：”
     */
    private String formatChatHistory(List<Message> chatHistory) {
        if (chatHistory.isEmpty()) {
            return "无历史对话"; // 空历史时给出明确提示，避免模板参数为空
        }
        return chatHistory.stream()
                .map(message -> {
                    String role = message instanceof UserMessage ? "用户" : "系统";
                    return String.format("%s：%s", role, message.getText());
                })
                .collect(Collectors.joining("\n")); // 每条对话换行分隔
    }

    // Builder模式逻辑不变，仅类名同步更新
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ChatClient.Builder chatClientBuilder;
        @Nullable
        private PromptTemplate promptTemplate;
        @Nullable
        private String targetSearchSystem;

        private Builder() {
        }

        public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
            this.chatClientBuilder = chatClientBuilder;
            return this;
        }

        public Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder targetSearchSystem(String targetSearchSystem) {
            this.targetSearchSystem = targetSearchSystem;
            return this;
        }

        public RewriteQueryTransformerWithHistory build() {
            return new RewriteQueryTransformerWithHistory(this.chatClientBuilder, this.promptTemplate, this.targetSearchSystem);
        }
    }
}