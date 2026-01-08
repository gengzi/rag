package com.gengzi.service.graph.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.config.PromptProperties;
import com.gengzi.model.dto.ChunkHit;
import com.gengzi.model.dto.GraphQueryResponse;
import com.gengzi.model.dto.QueryAnalysisResult;
import com.gengzi.repository.neo4j.ChunkRepository;
import com.gengzi.service.graph.GraphRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GraphRetrievalServiceImpl implements GraphRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(GraphRetrievalServiceImpl.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_DEPTH = 2;
    private static final int MIN_DEPTH = 1;
    private static final int MAX_DEPTH = 2;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final PromptProperties promptProperties;
    private final ChunkRepository chunkRepository;

    public GraphRetrievalServiceImpl(ChatModel chatModel,
                                     ObjectMapper objectMapper,
                                     PromptProperties promptProperties,
                                     ChunkRepository chunkRepository) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.promptProperties = promptProperties;
        this.chunkRepository = chunkRepository;
    }

    @Override
    public GraphQueryResponse retrieveChunks(String question, Integer neighborDepth, Integer limit) {
        String normalizedQuestion = question == null ? "" : question.trim();
        QueryAnalysisResult analysis = analyzeQuestion(normalizedQuestion);
        List<String> entities = normalizeList(analysis.getEntities());
        List<String> keywords = normalizeList(analysis.getKeywords());
        String searchType = normalizeSearchType(analysis.getSearchType());

        int resolvedDepth = resolveDepth(neighborDepth);
        int resolvedLimit = resolveLimit(limit);

        List<ChunkHit> chunks;
        if ("GLOBAL".equals(searchType)) {
            chunks = retrieveGlobalChunks(keywords, resolvedLimit);
            if (chunks.isEmpty() && !entities.isEmpty()) {
                chunks = retrieveLocalChunks(entities, resolvedDepth, resolvedLimit);
            }
        } else {
            chunks = retrieveLocalChunks(entities, resolvedDepth, resolvedLimit);
            if (chunks.isEmpty() && !keywords.isEmpty()) {
                chunks = retrieveGlobalChunks(keywords, resolvedLimit);
            }
        }

        return new GraphQueryResponse(
            normalizedQuestion,
            searchType,
            entities,
            keywords,
            chunks
        );
    }

    private QueryAnalysisResult analyzeQuestion(String question) {
        if (question == null || question.isBlank()) {
            return new QueryAnalysisResult("LOCAL", Collections.emptyList(), Collections.emptyList());
        }

        try {
            String promptTemplate = promptProperties.loadQueryAnalysisPrompt();
            String fullPrompt = promptTemplate.replace("{user_query}", question);
            Message userMessage = new UserMessage(fullPrompt);
            Prompt prompt = new Prompt(List.of(userMessage));
            String response = chatModel.call(prompt).getResult().getOutput().getText();

            String cleanedJson = validateJsonResponse(response);
            JsonNode node = objectMapper.readTree(cleanedJson);

            String searchType = getText(node.get("search_type"));
            List<String> entities = readEntityNames(node.get("entities"));
            List<String> keywords = readStringList(node.get("keywords"));

            return new QueryAnalysisResult(searchType, entities, keywords);
        } catch (Exception e) {
            logger.warn("Query analysis failed: {}", e.getMessage(), e);
            return new QueryAnalysisResult("LOCAL", Collections.emptyList(), Collections.emptyList());
        }
    }

    private List<ChunkHit> retrieveLocalChunks(List<String> entities, int depth, int limit) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChunkHit> rows = depth <= 1
            ? chunkRepository.findLocalChunkHitsDepth1(entities, limit)
            : chunkRepository.findLocalChunkHitsDepth2(entities, limit);

        return normalizeChunkHits(rows, false);
    }

    private List<ChunkHit> retrieveGlobalChunks(List<String> keywords, int limit) {
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChunkHit> rows = chunkRepository.findGlobalChunkHits(keywords, limit);
        return normalizeChunkHits(rows, true);
    }

    private List<ChunkHit> normalizeChunkHits(List<ChunkHit> rows, boolean includeCommunities) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChunkHit> hits = new ArrayList<>();
        for (ChunkHit row : rows) {
            String chunkId = row.getChunkId();
            String content = row.getContent();
            List<String> entityNames = normalizeList(row.getEntityNames());
            List<String> communityIds = includeCommunities
                ? normalizeList(row.getCommunityIds())
                : Collections.emptyList();
            hits.add(new ChunkHit(chunkId, content, entityNames, communityIds));
        }
        return hits;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isBlank()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }

    private int resolveDepth(Integer depth) {
        if (depth == null) {
            return DEFAULT_DEPTH;
        }
        return Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, depth));
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return limit;
    }

    private String normalizeSearchType(String searchType) {
        if (searchType == null || searchType.isBlank()) {
            return "LOCAL";
        }
        String normalized = searchType.trim().toUpperCase();
        return "GLOBAL".equals(normalized) ? "GLOBAL" : "LOCAL";
    }

    private String validateJsonResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            throw new IllegalArgumentException("Query analysis JSON response is empty");
        }

        String cleaned = jsonResponse.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }

        int startIndex = -1;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{' || c == '[') {
                startIndex = i;
                break;
            }
        }
        if (startIndex >= 0) {
            int endIndex = -1;
            for (int i = cleaned.length() - 1; i >= startIndex; i--) {
                char c = cleaned.charAt(i);
                if (c == '}' || c == ']') {
                    endIndex = i + 1;
                    break;
                }
            }
            if (endIndex > startIndex) {
                cleaned = cleaned.substring(startIndex, endIndex);
            }
        }

        return cleaned;
    }

    private String getText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = getText(item);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> readEntityNames(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
                continue;
            }
            JsonNode nameNode = item.get("name");
            if (nameNode != null && !nameNode.isNull()) {
                String value = nameNode.asText();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }
}
