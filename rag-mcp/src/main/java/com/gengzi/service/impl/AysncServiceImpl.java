package com.gengzi.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.gengzi.entity.MyOpenSearchDocument;
import com.gengzi.entity.OpenSearchExpandDocument;
import com.gengzi.entity.UserMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AysncServiceImpl {
    private static final Logger logger = LoggerFactory.getLogger(MemoryServiceImpl.class);
    private final VectorStore vectorStore;
    private final ChatClient.Builder extractorClientBuilder;

    public AysncServiceImpl(VectorStore vectorStore, ChatClient.Builder extractorChatClientBuilder) {
        this.vectorStore = vectorStore;
        this.extractorClientBuilder = extractorChatClientBuilder;
    }

    /**
     * 异步存储用户记忆 - 后台执行
     */
    @Async("memoryTaskExecutor")
    public void storeMemoryAsync(String userId, String content) {
        try {
            logger.info("Starting async memory storage for user: {}", userId);
            ChatClient extractorClient = extractorClientBuilder.build();
            List<UserMemory> entity = extractorClient.prompt().user(content).call().entity(new ParameterizedTypeReference<List<UserMemory>>() {
            });
            List<Document> documents = entity.stream().map(userMemory -> {
                OpenSearchExpandDocument openSearchExpandDocument = new OpenSearchExpandDocument();
                openSearchExpandDocument.setId(IdUtil.simpleUUID());
                openSearchExpandDocument.setContent(userMemory.getContent());
                openSearchExpandDocument.setMetadata(Map.of(
                        "user_id", userId,
                        "type", userMemory.getType().toString(),
                        "confidence", userMemory.getConfidence()
                ));

                openSearchExpandDocument.setType(userMemory.getType().toString());
                openSearchExpandDocument.setUserId(userId);
                openSearchExpandDocument.setCreateTime(DateUtil.format(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSS"));
                openSearchExpandDocument.setUpdateTime(DateUtil.format(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSS"));
                openSearchExpandDocument.setConfidence(userMemory.getConfidence());
                return new MyOpenSearchDocument(openSearchExpandDocument);
            }).collect(Collectors.toList());

            vectorStore.add(documents);
            logger.info("Async memory storage completed successfully for user: {}", userId);
        } catch (Exception e) {
            logger.error("Error in async memory storage for user: {}", userId, e);
        }
    }
}
