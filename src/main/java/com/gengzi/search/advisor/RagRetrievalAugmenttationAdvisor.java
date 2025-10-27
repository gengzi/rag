package com.gengzi.search.advisor;


import com.gengzi.search.processors.RagDocumentPostProcessor;
import com.gengzi.search.query.QueryTranslation;
import com.gengzi.search.query.RagContextualQueryAugmenter;
import com.gengzi.search.query.RewriteQueryTransformerWithHistory;
import com.gengzi.search.query.ToolsRagTransformer;
import com.gengzi.search.template.RagPromptTemplate;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * RAG 检索增强器
 */
@Component
public class RagRetrievalAugmenttationAdvisor {


    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RagPromptTemplate ragPromptTemplate;


    @Autowired
    private QueryTranslation queryTranslation;


    @Autowired
    private RagDocumentPostProcessor ragDocumentPostProcessor;


    @Autowired
    @Qualifier("openAiChatModel")
    private OpenAiChatModel chatModel;

    @Autowired
    private ToolsRagTransformer toolsRagTransformer;


    @Bean("ragAdvisor")
    public Advisor createAdvisor() {

        RewriteQueryTransformerWithHistory rewriteQueryTransformerWithHistory = RewriteQueryTransformerWithHistory.builder()
                .chatClientBuilder(ChatClient.builder(chatModel)).build();
        // 查询压缩，将之前的历史对话和当前问题压缩为一个独立的查询
        CompressionQueryTransformer compressionQueryTransformer =
                CompressionQueryTransformer.builder().chatClientBuilder(ChatClient.builder(chatModel)).build();

        Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                // 用于转换输入查询，使得更有效的执行检索
//                .queryTransformers(toolsRagTransformer, rewriteQueryTransformerWithHistory)
                .queryTransformers(rewriteQueryTransformerWithHistory)
                // 检索器
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        // 相似度
                        .similarityThreshold(0.60)
                        // topk
                        .topK(7)
                        // 使用的向量数据库
                        .vectorStore(vectorStore)
                        .build())
                // 查询参数 这里设置模板
                .queryAugmenter(RagContextualQueryAugmenter.builder()
                        // 设置提示词模板
                        .promptTemplate(ragPromptTemplate.ragPromptTemplate())
                        .emptyContextPromptTemplate(ragPromptTemplate.ragPromptTemplateNoContext())
//                        .allowEmptyContext(true)
                        .build())
                // 将检索到的文档处理之后，再传递给大模型
                .documentPostProcessors(ragDocumentPostProcessor)
                // 将多个数据源检索到的文档合并为单个文档
//                .documentJoiner()
                .build();
        return retrievalAugmentationAdvisor;
    }

}
