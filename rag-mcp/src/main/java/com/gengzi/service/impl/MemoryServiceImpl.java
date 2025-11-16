/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gengzi.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 用户记忆服务层
 */
@Service("memoryServiceImpl")
public class MemoryServiceImpl implements MemoryService {
    private static final Logger logger = LoggerFactory.getLogger(MemoryServiceImpl.class);

    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    private final VectorStore vectorStore;
    private final ChatClient.Builder clientBuilder;
    private final ChatClient.Builder extractorClientBuilder;
    private final AysncServiceImpl aysncServiceImpl;


    @Autowired
    public MemoryServiceImpl(VectorStore openSearchVectorStore, ApplicationContext applicationContext,
                             ChatClient.Builder queryExpanderChatClientBuilder, ChatClient.Builder extractorChatClientBuilder,
                             AysncServiceImpl aysncServiceImpl) {
        this.vectorStore = openSearchVectorStore;
        this.objectMapper = new ObjectMapper();
        this.applicationContext = applicationContext;
        this.clientBuilder = queryExpanderChatClientBuilder;
        this.extractorClientBuilder = extractorChatClientBuilder;
        this.aysncServiceImpl = aysncServiceImpl;
    }


    /**
     * 查询用户历史记忆
     * <p>
     * 将用户问题进行embedding，
     * 通过向量检索查询向量数据库，
     * 得到相似度超过0.70的top3的内容，
     * 作为用户记忆添加到用户聊天中
     *
     * @param userId 用户id
     * @param query  用户问题
     * @return
     */
    public String searchMemory(String userId, String query) {
        try {
            logger.info("Searching memories for user: {}, query:{}", userId, query);
            // 用户问题扩写为三个问题
            MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                    .chatClientBuilder(clientBuilder)
                    .numberOfQueries(3)
                    .build();
            List<Query> queries = queryExpander.expand(new Query(query));
            queries.forEach(querylog -> {
                logger.debug("Expanded query: {}", querylog);
            });
            DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .similarityThreshold(0.90)
                    .topK(3)
                    .filterExpression(new FilterExpressionBuilder()
                            // 只过滤当前用户的
                            .eq("user_id", userId)
                            .build())
                    .build();
            final Map<Query, List<List<Document>>> documentsForQuery = new HashMap<>();

            for (Query currentQuery : queries) {
                // 获取的记忆信息
                List<Document> documents = retriever.retrieve(currentQuery);
                // 将获取的记忆信息合并
                documentsForQuery.put(currentQuery, List.of(documents));
            }
            logger.debug("Documents for query: {}", documentsForQuery);
            DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
            List<Document> mergeDocument = documentJoiner.join(documentsForQuery);

            String userMemories = mergeDocument.stream().map(Document::getText).collect(Collectors.joining("\n"));
            logger.info("Merged document: {}", userMemories);
            return userMemories;
        } catch (Exception e) {
            logger.error("Error searching memories for user: {}", userId, e);
            return "未找到用户历史喜好";
        }
    }

    /**
     * 存储用户记忆 - 异步方法，立即返回成功状态
     */
    public String storeMemory(String userId, String content) {
        // 立即返回成功状态
        logger.info("Memory storage request received for user: {}, content: {}", userId, content);

        // 通过ApplicationContext获取代理对象来调用异步方法
//        MemoryServiceImpl self = (MemoryServiceImpl) applicationContext.getBean("memoryServiceImpl");
        // 从代理对象中获取原始实现类对象
//        MemoryServiceImpl target = (MemoryServiceImpl) AopProxyUtils.getSingletonTarget(applicationContext.getBean("memoryServiceImpl"));
        aysncServiceImpl.storeMemoryAsync(userId, content);
//        self.storeMemoryAsync(userId, content);

        return "成功存储用户喜好";
    }


}