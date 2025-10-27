package com.gengzi.search.query;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RagContextualQueryAugmenter implements QueryAugmenter {


    private static final Logger logger = LoggerFactory.getLogger(RagContextualQueryAugmenter.class);

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
            Context information is below.
            
            ---------------------
            {context}
            ---------------------
            
            Given the context information and no prior knowledge, answer the query.
            
            Follow these rules:
            
            1. If the answer is not in the context, just say that you don't know.
            2. Avoid statements like "Based on the context..." or "The provided information...".
            
            Query: {query}
            
            Answer:
            """);

    private static final PromptTemplate DEFAULT_EMPTY_CONTEXT_PROMPT_TEMPLATE = new PromptTemplate("""
            The user query is outside your knowledge base.
            Politely inform the user that you can't answer it.
            """);

    private static final boolean DEFAULT_ALLOW_EMPTY_CONTEXT = false;

    /**
     * Default document formatter that just joins document text with newlines
     */
    private static final Function<List<Document>, String> DEFAULT_DOCUMENT_FORMATTER = documents -> documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining(System.lineSeparator()));

    private final PromptTemplate promptTemplate;

    private final PromptTemplate emptyContextPromptTemplate;

    private final boolean allowEmptyContext;

    private final Function<List<Document>, String> documentFormatter;

    public RagContextualQueryAugmenter(@Nullable PromptTemplate promptTemplate,
                                       @Nullable PromptTemplate emptyContextPromptTemplate, @Nullable Boolean allowEmptyContext,
                                       @Nullable Function<List<Document>, String> documentFormatter) {
        this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.emptyContextPromptTemplate = emptyContextPromptTemplate != null ? emptyContextPromptTemplate
                : DEFAULT_EMPTY_CONTEXT_PROMPT_TEMPLATE;
        this.allowEmptyContext = allowEmptyContext != null ? allowEmptyContext : DEFAULT_ALLOW_EMPTY_CONTEXT;
        this.documentFormatter = documentFormatter != null ? documentFormatter : DEFAULT_DOCUMENT_FORMATTER;
        PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "query", "context");
    }

    public static RagContextualQueryAugmenter.Builder builder() {
        return new RagContextualQueryAugmenter.Builder();
    }

    @Override
    public Query augment(Query query, List<Document> documents) {
        Assert.notNull(query, "query cannot be null");
        Assert.notNull(documents, "documents cannot be null");

        logger.debug("Augmenting query with contextual data");

        if (documents.isEmpty()) {
            return augmentQueryWhenEmptyContext(query);
        }

        // 1. Collect content from documents.
        String documentContext = this.documentFormatter.apply(documents);

        // 2. Define prompt parameters.
        Map<String, Object> promptParameters = Map.of("query", query.text(), "context", documentContext);

        // 3. Augment user prompt with document context.
        return new Query(this.promptTemplate.render(promptParameters));
    }

    private Query augmentQueryWhenEmptyContext(Query query) {
        if (this.allowEmptyContext) {
            logger.debug("Empty context is allowed. Returning the original query.");
            return query;
        }
        Map<String, Object> promptParameters = Map.of("query", query.text());

        logger.debug("Empty context is not allowed. Returning a specific query for empty context.");
        return new Query(this.emptyContextPromptTemplate.render(promptParameters));
    }

    public static class Builder {

        private PromptTemplate promptTemplate;

        private PromptTemplate emptyContextPromptTemplate;

        private Boolean allowEmptyContext;

        private Function<List<Document>, String> documentFormatter;

        public RagContextualQueryAugmenter.Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public RagContextualQueryAugmenter.Builder emptyContextPromptTemplate(PromptTemplate emptyContextPromptTemplate) {
            this.emptyContextPromptTemplate = emptyContextPromptTemplate;
            return this;
        }

        public RagContextualQueryAugmenter.Builder allowEmptyContext(Boolean allowEmptyContext) {
            this.allowEmptyContext = allowEmptyContext;
            return this;
        }

        public RagContextualQueryAugmenter.Builder documentFormatter(Function<List<Document>, String> documentFormatter) {
            this.documentFormatter = documentFormatter;
            return this;
        }

        public RagContextualQueryAugmenter build() {
            return new RagContextualQueryAugmenter(this.promptTemplate, this.emptyContextPromptTemplate,
                    this.allowEmptyContext, this.documentFormatter);
        }

    }
}
