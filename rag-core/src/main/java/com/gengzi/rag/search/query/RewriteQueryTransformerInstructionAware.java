/*
 * 改造后的RewriteQueryTransformer，支持携带历史对话记录
 */
package com.gengzi.rag.search.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 指令感知嵌入（Instruction-aware Embedding） ,将用户问题增强，增加Instruction ，以帮助embeding 模型理解用户问题
 * <p>
 * 也可以设计为动态Instruction，根据不同的查询问题，设计不同的Instruction
 */
public class RewriteQueryTransformerInstructionAware implements QueryTransformer {

    private static final Logger logger = LoggerFactory.getLogger(RewriteQueryTransformerInstructionAware.class);

    //  rag默认Instruction 指令
    // 给定一个用户问题，从知识库中检索可以回答该问题的相关段落。
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            Instruct: Given a user question, retrieve relevant passages from the knowledge base that can answer the question.
            Query: {query}
            """);

    private static final PromptTemplate DYNAMIC_INSTRUCTION = new PromptTemplate("""
            {Instruct}
            Query: {query}
            """);

    private static final String LLM_INSTRUCTION_PROMPT_TEMPLATE = """
            You are an expert retrieval system optimizer. Your task is to generate a precise, concise instruction for a text embedding model to retrieve the most relevant documents from a knowledge base.
            
            Given a user's question, output ONLY a single line in the exact format:
            "Instruct: [description]"
            
            The description should:
            - Be in English (even if the question is in another language)
            - Clearly state the retrieval task
            - Specify the type of knowledge expected (e.g., technical documentation, HR policy, medical guideline, legal clause, customer support article)
            - Be general enough to match multiple relevant documents, but specific to the domain
            - NOT include the user's question itself
            - NOT add any extra text, explanation, or punctuation beyond the format
            
            Examples:
            User: "How do I reset my password?"
            Output: Instruct: Retrieve customer account management procedures for password reset.
            
            User: "产假有多少天？"
            Output: Instruct: Retrieve company HR policies regarding maternity leave duration.
            
            User: "MySQL 启动失败怎么办？"
            Output: Instruct: Retrieve technical troubleshooting guides for MySQL server startup issues.
            
            Now, generate the instruction for the following user question:
            """;

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final Boolean isDynamicInstruction;

    // 构造器逻辑不变，仅提示词模板默认值更新
    public RewriteQueryTransformerInstructionAware(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate, Boolean isDynamicInstruction) {
        Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");

        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.isDynamicInstruction = isDynamicInstruction != null ? isDynamicInstruction : false;

        PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "query");
    }

    // Builder模式逻辑不变，仅类名同步更新
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Query transform(Query query) {
        Assert.notNull(query, "query cannot be null");
        logger.debug("指令感知嵌入模板{}", this.promptTemplate);


        if (isDynamicInstruction) {
            // 可以让大模型进行指令引导
            var rewrittenQueryText = this.chatClient.prompt()
                    .system(LLM_INSTRUCTION_PROMPT_TEMPLATE)
                    .user(query.text())
                    .call()
                    .content();
            logger.debug("动态指令:{}", rewrittenQueryText);
            if (!StringUtils.hasText(rewrittenQueryText)) {
                logger.warn("动态指令感知嵌入生成异常");
                return query;
            }
            rewrittenQueryText = DYNAMIC_INSTRUCTION.render(Map.of("Instruct", rewrittenQueryText, "query", query.text()));
            logger.debug("动态指令增强后的用户问题:{}", rewrittenQueryText);
            return query.mutate()
                    .text(rewrittenQueryText)
                    .build();
        }


        // 根据默认指令增强查询
        String instructRender = DEFAULT_PROMPT_TEMPLATE.render(Map.of("query", query.text()));
        // 4. 返回新Query时，保留原始历史对话 metadata（便于后续多轮使用）
        return query.mutate()
                .text(instructRender)
                .build();
    }


    public static final class Builder {
        private ChatClient.Builder chatClientBuilder;
        @Nullable
        private PromptTemplate promptTemplate;
        private Boolean isDynamicInstruction;

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

        public Builder isDynamicInstruction(Boolean isDynamicInstruction) {
            this.isDynamicInstruction = isDynamicInstruction;
            return this;
        }


        public RewriteQueryTransformerInstructionAware build() {
            return new RewriteQueryTransformerInstructionAware(this.chatClientBuilder, this.promptTemplate, this.isDynamicInstruction);
        }
    }
}