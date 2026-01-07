package com.gengzi.service.graph.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.config.PromptProperties;
import com.gengzi.model.dto.EntityRelationDTO;
import com.gengzi.model.neo4j.Chunk;
import com.gengzi.model.neo4j.EntityRelation;
import com.gengzi.model.neo4j.KnowledgeEntity;
import com.gengzi.repository.neo4j.ChunkRepository;
import com.gengzi.repository.neo4j.KnowledgeEntityRepository;
import com.gengzi.service.graph.CommunityGraphService;
import com.gengzi.service.graph.GraphExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 图谱提取服务实现
 * 使用大模型从文本中提取实体和关系
 */
@Service
public class GraphExtractionServiceImpl implements GraphExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(GraphExtractionServiceImpl.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final PromptProperties promptProperties;
    private final KnowledgeEntityRepository knowledgeEntityRepository;
    private final ChunkRepository chunkRepository;
    private final CommunityGraphService communityGraphService;

    public GraphExtractionServiceImpl(ChatModel chatModel,
                                       ObjectMapper objectMapper,
                                       PromptProperties promptProperties,
                                       KnowledgeEntityRepository knowledgeEntityRepository,
                                       ChunkRepository chunkRepository,
                                       CommunityGraphService communityGraphService) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.promptProperties = promptProperties;
        this.knowledgeEntityRepository = knowledgeEntityRepository;
        this.chunkRepository = chunkRepository;
        this.communityGraphService = communityGraphService;
    }

    @Override
    public String extractEntitiesAndRelations(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            logger.warn("输入文本为空，无法提取实体和关系");
            return "[]";
        }

        try {
            // 从配置加载提示词模板
            String promptTemplate = promptProperties.loadEntityRecognitionPrompt();

            // 构建完整提示词
            String fullPrompt = promptTemplate.replace("{input_text}", chunkText);

            // 调用大模型
            Message userMessage = new UserMessage(fullPrompt);
            Prompt prompt = new Prompt(List.of(userMessage));
            String response = chatModel.call(prompt).getResult().getOutput().getText();

            // 清理并验证返回的 JSON 格式
            String cleanedJson = validateJsonResponse(response);

            logger.info("成功从文本中提取实体和关系，文本长度: {}, 响应长度: {}",
                    chunkText.length(), cleanedJson.length());

            return cleanedJson;

        } catch (Exception e) {
            logger.error("提取实体和关系时发生错误: {}", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * 清理并验证 JSON 响应格式
     * 处理 LLM 返回的常见格式问题，如：
     * - 移除 markdown 代码块标记 (```json 和 ```)
     * - 移除前后的空白字符
     * - 移除可能的前后导说明文字
     *
     * @param jsonResponse 原始 JSON 响应字符串
     * @return 清理后的有效 JSON 字符串
     * @throws RuntimeException 如果清理后的字符串仍不是有效的 JSON
     */
    private String validateJsonResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            throw new RuntimeException("JSON 响应为空");
        }

        String cleaned = jsonResponse;

        // 1. 移除前后的空白字符
        cleaned = cleaned.trim();

        // 2. 移除 markdown 代码块标记 (```json 或 ``` ... ```)
        if (cleaned.startsWith("```")) {
            // 找到第一个换行符，移除第一行（可能是 ```json 或 ```）
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline);
            }

            // 移除结尾的 ```
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }

        // 3. 尝试提取 JSON 对象或数组
        // 找到第一个 { 或 [
        int startIndex = -1;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{' || c == '[') {
                startIndex = i;
                break;
            }
        }

        if (startIndex >= 0) {
            // 找到最后一个 } 或 ]
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

        // 4. 验证清理后的 JSON 是否有效
        try {
            objectMapper.readTree(cleaned);
            logger.debug("成功清理并验证 JSON 响应，原始长度: {}, 清理后长度: {}",
                    jsonResponse.length(), cleaned.length());
            return cleaned;
        } catch (JsonProcessingException e) {
            logger.error("清理后的 JSON 仍然无效。原始响应: {}\n清理后: {}",
                    jsonResponse, cleaned);
            throw new RuntimeException("无效的 JSON 响应格式", e);
        }
    }

    @Override
    @Transactional(value = "transactionManager")
    public List<EntityRelationDTO> extractAndSaveEntities(String chunkText, String... chunkId) {
        if (chunkText == null || chunkText.isBlank()) {
            logger.warn("输入文本为空，无法提取实体和关系");
            return Collections.emptyList();
        }

        try {
            // 1. 调用 LLM 提取实体和关系（获取 JSON 字符串）
            String jsonResponse = extractEntitiesAndRelations(chunkText);

            // 2. 解析 JSON 为 DTO 列表
            List<EntityRelationDTO> relations = parseEntityRelations(jsonResponse);

            if (relations.isEmpty()) {
                logger.info("未提取到任何实体关系");
                return relations;
            }

            // 3. 转换为 Neo4j 实体并保存
            Set<KnowledgeEntity> entities = saveEntitiesToNeo4j(relations);

            // 4. 如果提供了 chunkId，建立 Chunk 与 Entity 的关系
            if (chunkId != null && chunkId.length > 0 && !chunkId[0].isBlank()) {
                linkChunkToEntities(chunkId[0], entities);
            }
            communityGraphService.rebuildCommunities();

            logger.info("成功提取并保存 {} 个实体关系到 Neo4j", relations.size());
            return relations;

        } catch (Exception e) {
            logger.error("提取并保存实体关系时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("提取并保存实体关系失败", e);
        }
    }

    /**
     * 解析 JSON 字符串为实体关系 DTO 列表
     */
    private List<EntityRelationDTO> parseEntityRelations(String jsonResponse) {
        try {
            return objectMapper.readValue(jsonResponse, new TypeReference<List<EntityRelationDTO>>() {});
        } catch (JsonProcessingException e) {
            logger.error("解析实体关系 JSON 失败: {}", jsonResponse, e);
            throw new RuntimeException("无法解析实体关系 JSON", e);
        }
    }

    /**
     * 将实体关系 DTO 列表保存到 Neo4j 图数据库
     * 使用 Neo4j Repository 和 Client 的混合方式
     *
     * @param relations 实体关系 DTO 列表
     * @return 保存的所有实体集合（去重后）
     */
    private Set<KnowledgeEntity> saveEntitiesToNeo4j(List<EntityRelationDTO> relations) {
        // 收集所有唯一的实体
        Map<String, KnowledgeEntity> entityMap = new HashMap<>();

        for (EntityRelationDTO dto : relations) {
            // 处理 head 实体
            String headId = generateEntityId(dto.getHead());
            KnowledgeEntity headEntity = entityMap.computeIfAbsent(headId,
                id -> createKnowledgeEntity(dto.getHead(), dto.getHeadType(), dto.getHeadDesc()));

            // 处理 tail 实体
            String tailId = generateEntityId(dto.getTail());
            KnowledgeEntity tailEntity = entityMap.computeIfAbsent(tailId,
                id -> createKnowledgeEntity(dto.getTail(), dto.getTailType(), dto.getTailDesc()));

            // 添加关系
            EntityRelation relation = new EntityRelation();
            relation.setKey(dto.getRelation());
            relation.setDescription(dto.getRelation() + "关系");
            relation.setTargetEntity(tailEntity);

            headEntity.getRelations().add(relation);
        }

        // 使用 Repository 保存所有实体（包括关系）
        for (KnowledgeEntity entity : entityMap.values()) {
            knowledgeEntityRepository.save(entity);
            logger.debug("成功保存实体: {} ({})", entity.getName(), entity.getType());
        }

        logger.info("成功保存 {} 个实体到 Neo4j", entityMap.size());

        // 返回保存的实体集合
        return new HashSet<>(entityMap.values());
    }

    /**
     * 生成实体 ID（使用简单的 slug 方式）
     */
    private String generateEntityId(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        // 简单的 slug 转换：转小写，空格替换为下划线
        return "ent:" + name.toLowerCase().replaceAll("\\s+", "_");
    }

    /**
     * 创建 KnowledgeEntity 对象
     */
    private KnowledgeEntity createKnowledgeEntity(String name, String type, String description) {
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(generateEntityId(name));
        entity.setName(name);
        entity.setType(type);
        entity.setDescription(description);
        return entity;
    }

    /**
     * 建立 Chunk 与 Entity 之间的 MENTIONS 关系
     *
     * @param chunkId 分块ID
     * @param entities 实体集合
     */
    private void linkChunkToEntities(String chunkId, Set<KnowledgeEntity> entities) {
        if (chunkId == null || chunkId.isBlank() || entities == null || entities.isEmpty()) {
            logger.debug("ChunkId 或实体集合为空，跳过建立关系");
            return;
        }

        try {
            // 查询 Chunk 节点
            Optional<Chunk> chunkOpt = chunkRepository.findById(chunkId);
            if (chunkOpt.isEmpty()) {
                logger.warn("未找到 Chunk 节点: {}", chunkId);
                return;
            }

            Chunk chunk = chunkOpt.get();

            // 添加实体到 Chunk 的 entities 列表
            // 由于 Chunk 实体中已经定义了 @Relationship(type = "MENTIONS")
            // 我们只需要将实体添加到列表中并保存
            for (KnowledgeEntity entity : entities) {
                if (!chunk.getEntities().contains(entity)) {
                    chunk.getEntities().add(entity);
                    logger.debug("建立 Chunk {} 与 Entity {} 的 MENTIONS 关系", chunkId, entity.getName());
                }
            }

            // 保存 Chunk（会自动创建 MENTIONS 关系）
            chunkRepository.save(chunk);

            logger.info("成功为 Chunk {} 建立与 {} 个实体的 MENTIONS 关系", chunkId, entities.size());

        } catch (Exception e) {
            logger.error("建立 Chunk {} 与实体关系失败: {}", chunkId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }
}
