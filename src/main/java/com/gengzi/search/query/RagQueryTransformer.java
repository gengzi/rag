package com.gengzi.search.query;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.stereotype.Component;

@Component
public class RagQueryTransformer implements QueryTransformer {
    private static final Logger logger = LoggerFactory.getLogger(RagQueryTransformer.class);

    @Override
    public Query transform(Query query) {
        logger.info("query: {}", query);
        // 用户问题
        String text = query.text();

        // 改写用户问题，让问题更加清晰

        // 拆分用户问题，拆分成三个子问题

        // 过滤关键词，进行动态过滤



        return null;
    }
}
