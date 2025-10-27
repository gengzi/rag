package com.gengzi.search.processors;


import com.gengzi.config.chat.RerankerParamsConfig;
import com.gengzi.reranker.RerankerModel;
import com.gengzi.reranker.request.RerankInstructions;
import com.gengzi.reranker.request.RerankModelRequest;
import com.gengzi.reranker.response.RerankModelResult;
import com.gengzi.reranker.response.RerankResultData;
import com.gengzi.reranker.response.RerankerModelResponse;
import com.gengzi.search.query.RagContextualQueryAugmenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 对检索到的文档进行后处理
 */
@Component
public class RagDocumentPostProcessor implements DocumentPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RagContextualQueryAugmenter.class);
    @Autowired
    @Qualifier("defaultRerankModel")
    private RerankerModel rerankerModel;

    @Autowired
    private RerankerParamsConfig rerankerParamsConfig;


    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents.size() <= 0) {
            return documents;
        }
        // 查询父文档内容，将相关的内容都塞给大模型
        // 重排序
        RerankInstructions rerankInstructions = new RerankInstructions();
        rerankInstructions.setQuery(query.text());
        rerankInstructions.setDocuments(documents.stream().map(Document::getText).collect(Collectors.toList()));
//        rerankInstructions.setDocuments(documents.stream().map(d -> MarkdownCleaner.clean(d.getText())).collect(Collectors.toList()));
        //  rerank awen 支持说明，建议使用英文，会提升性能1%-5%。增加此参数
        rerankInstructions.setInstruction(rerankerParamsConfig.getInstruction());
        RerankerModelResponse call = rerankerModel.call(new RerankModelRequest(rerankInstructions, null));
        LinkedList<Document> sortedDocuments = new LinkedList<>();

        List<RerankModelResult> results = call.getResults();
        logger.info("rerankerModelResponse:{}", call);
        for (RerankModelResult result : results) {
            RerankResultData output = result.getOutput();
            Double relevanceScore = output.getRelevanceScore();
            Integer index = output.getIndex();
            if (relevanceScore > 0.1) {
                logger.debug("分数:{},相关的文档:{}", relevanceScore, documents.get(index).getText());
                sortedDocuments.add(documents.get(index));
            } else {
                logger.info("分数:{},排除不相关的文档:{}", relevanceScore, documents.get(index).getText());
            }
        }
        return sortedDocuments;
    }
}
