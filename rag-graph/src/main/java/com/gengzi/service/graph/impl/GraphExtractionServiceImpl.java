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
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 图谱提取服务实现类
 *
 * <p>负责从文本中提取实体和关系，并构建知识图谱。主要功能包括：</p>
 * <ul>
 *   <li>使用 LLM 从文本中提取实体和关系</li>
 *   <li>将提取结果保存到 Neo4j 图数据库</li>
 *   <li>建立文档分块与实体的关联关系</li>
 *   <li>触发社区发现算法</li>
 *   <li>生成社区总结报告</li>
 * </ul>
 *
 * <p>核心流程：</p>
 * <pre>
 * 文本输入 → LLM 提取实体关系 → 保存到 Neo4j → 建立关联 → 社区发现 → 生成报告
 * </pre>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 * @see GraphExtractionService
 */
@Service
public class GraphExtractionServiceImpl implements GraphExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(GraphExtractionServiceImpl.class);

    /**
     * 生成社区报告时使用的最大文档分块数量
     * 限制此数量以避免 LLM 输入过长
     */
    private static final int COMMUNITY_REPORT_CHUNK_LIMIT = 30;

    /**
     * 生成社区报告时的最大字符数限制
     * 超过此长度的文本将被截断，以控制 LLM 输入大小
     */
    private static final int COMMUNITY_REPORT_MAX_CHARS = 8000;

    /**
     * Spring AI ChatModel，用于调用 LLM 进行实体提取和报告生成
     */
    private final ChatModel chatModel;

    /**
     * Jackson ObjectMapper，用于 JSON 序列化和反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 提示词配置属性，用于加载 LLM 提示词模板
     */
    private final PromptProperties promptProperties;

    /**
     * 知识实体 Repository，用于 Neo4j 实体的 CRUD 操作
     */
    private final KnowledgeEntityRepository knowledgeEntityRepository;

    /**
     * 文档分块 Repository，用于文档分块的 CRUD 操作
     */
    private final ChunkRepository chunkRepository;

    /**
     * 社区发现服务，用于执行 Louvain 社区划分算法
     */
    private final CommunityGraphService communityGraphService;

    /**
     * Neo4j 客户端，用于执行自定义 Cypher 查询
     */
    private final Neo4jClient neo4jClient;

    /**
     * 构造函数，通过依赖注入初始化所有依赖项
     *
     * @param chatModel Spring AI ChatModel 实例
     * @param ObjectMapper Jackson ObjectMapper 实例
     * @param promptProperties 提示词配置实例
     * @param knowledgeEntityRepository 知识实体 Repository
     * @param chunkRepository 文档分块 Repository
     * @param communityGraphService 社区发现服务
     * @param neo4jClient Neo4j 客户端
     */
    public GraphExtractionServiceImpl(ChatModel chatModel,
                                       ObjectMapper objectMapper,
                                       PromptProperties promptProperties,
                                       KnowledgeEntityRepository knowledgeEntityRepository,
                                       ChunkRepository chunkRepository,
                                       CommunityGraphService communityGraphService,
                                       Neo4jClient neo4jClient) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.promptProperties = promptProperties;
        this.knowledgeEntityRepository = knowledgeEntityRepository;
        this.chunkRepository = chunkRepository;
        this.communityGraphService = communityGraphService;
        this.neo4jClient = neo4jClient;
    }

    /**
     * 从文本中提取实体和关系（仅提取，不保存）
     *
     * <p>此方法执行以下操作：</p>
     * <ol>
     *   <li>加载实体识别提示词模板</li>
     *   <li>将输入文本填充到提示词中</li>
     *   <li>调用 LLM 进行实体和关系提取</li>
     *   <li>清理和验证 LLM 返回的 JSON 格式</li>
     *   <li>返回 JSON 格式的实体关系列表</li>
     * </ol>
     *
     * <p>注意：此方法仅提取数据，不会保存到数据库。
     * 如需保存，请使用 {@link #extractAndSaveEntities(String, String...)} 方法。</p>
     *
     * @param chunkText 要提取的文本内容
     * @return JSON 格式的实体关系列表，如果提取失败则返回 "[]"
     */
    @Override
    public String extractEntitiesAndRelations(String chunkText) {
        // 参数校验
        if (chunkText == null || chunkText.isBlank()) {
            logger.warn("输入文本为空，无法提取实体和关系");
            return "[]";
        }

        try {
            // 步骤1: 从配置加载实体识别提示词模板
            String promptTemplate = promptProperties.loadEntityRecognitionPrompt();

            // 步骤2: 将输入文本填充到提示词模板中
            String fullPrompt = promptTemplate.replace("{input_text}", chunkText);

            // 步骤3: 调用 LLM 进行实体和关系提取
            Message userMessage = new UserMessage(fullPrompt);
            Prompt prompt = new Prompt(List.of(userMessage));
            String response = chatModel.call(prompt).getResult().getOutput().getText();

            // 步骤4: 清理并验证 LLM 返回的 JSON 格式
            // LLM 可能返回 markdown 格式的 JSON（```json ... ```），需要清理
            String cleanedJson = validateJsonResponse(response);

            logger.info("成功从文本中提取实体和关系，文本长度: {}, 响应长度: {}",
                    chunkText.length(), cleanedJson.length());

            return cleanedJson;

        } catch (Exception e) {
            // 捕获异常并返回空数组，避免影响主流程
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

    /**
     * 从文本中提取实体和关系，并保存到 Neo4j 数据库
     *
     * <p>这是核心方法，整合了整个图谱构建流程：</p>
     * <ol>
     *   <li>调用 LLM 提取实体和关系</li>
     *   <li>解析 JSON 结果为 DTO 对象</li>
     *   <li>保存实体和关系到 Neo4j</li>
     *   <li>建立 Chunk 与 Entity 的 MENTIONS 关系（如果提供了 chunkId）</li>
     *   <li>触发社区发现算法，重建社区结构</li>
     *   <li>生成社区总结报告</li>
     * </ol>
     *
     * <p>注意：此方法使用了事务注解，确保所有数据库操作的原子性。
     * 如果任何步骤失败，整个流程将回滚。</p>
     *
     * @param chunkText 要提取的文本内容
     * @param chunkId 可选的文档分块 ID，如果提供则建立 Chunk-Entity 关系
     * @return 提取的实体关系 DTO 列表
     * @throws RuntimeException 如果提取或保存失败
     */
    @Override
    @Transactional(value = "transactionManager")
    public List<EntityRelationDTO> extractAndSaveEntities(String chunkText, String... chunkId) {
        // 参数校验
        if (chunkText == null || chunkText.isBlank()) {
            logger.warn("输入文本为空，无法提取实体和关系");
            return Collections.emptyList();
        }

        try {
            // 步骤1: 调用 LLM 提取实体和关系（获取 JSON 字符串）
            String jsonResponse = extractEntitiesAndRelations(chunkText);

            // 步骤2: 解析 JSON 为 DTO 列表
            List<EntityRelationDTO> relations = parseEntityRelations(jsonResponse);

            // 如果没有提取到任何关系，直接返回空列表
            if (relations.isEmpty()) {
                logger.info("未提取到任何实体关系");
                return relations;
            }

            // 步骤3: 转换为 Neo4j 实体并保存
            // 返回保存的所有实体（去重后）
            Set<KnowledgeEntity> entities = saveEntitiesToNeo4j(relations);

            // 步骤4: 如果提供了 chunkId，建立 Chunk 与 Entity 的 MENTIONS 关系
            if (chunkId != null && chunkId.length > 0 && !chunkId[0].isBlank()) {
                linkChunkToEntities(chunkId[0], entities);
            }

            // 步骤5: 触发社区发现算法，重建社区结构
            // Louvain 算法会根据实体关系重新划分社区
            communityGraphService.rebuildCommunities();

            // 步骤6: 生成社区总结报告
            // 为每个社区调用 LLM 生成标题、摘要、评分和关键词
            generateCommunityReports();

            logger.info("成功提取并保存 {} 个实体关系到 Neo4j", relations.size());
            return relations;

        } catch (Exception e) {
            // 记录错误并抛出运行时异常，触发事务回滚
            logger.error("提取并保存实体关系时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("提取并保存实体关系失败", e);
        }
    }

    /**
     * 解析 JSON 字符串为实体关系 DTO 列表
     *
     * <p>使用 Jackson ObjectMapper 将 JSON 字符串反序列化为
     * EntityRelationDTO 对象列表。</p>
     *
     * @param jsonResponse JSON 格式的实体关系列表
     * @return 实体关系 DTO 列表
     * @throws RuntimeException 如果 JSON 解析失败
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
     *
     * <p>此方法执行以下操作：</p>
     * <ol>
     *   <li>遍历所有实体关系 DTO，提取实体信息</li>
     *   <li>为每个实体生成唯一 ID（使用 slug 方式）</li>
     *   <li>创建 KnowledgeEntity 对象并建立关系</li>
     *   <li>使用 Repository 保存实体和关系到 Neo4j</li>
     * </ol>
     *
     * <p>数据模型：</p>
     * <pre>
     * (headEntity:Entity)-[:RELATION_TYPE]->(tailEntity:Entity)
     * </pre>
     *
     * <p>注意：此方法会自动去重实体。如果多个关系涉及同一个实体，
     * 该实体只会被创建一次，但会建立多个关系。</p>
     *
     * @param relations 实体关系 DTO 列表
     * @return 保存的所有实体集合（去重后）
     */
    private Set<KnowledgeEntity> saveEntitiesToNeo4j(List<EntityRelationDTO> relations) {
        // 用于收集所有唯一的实体（去重）
        // 使用 Map 避免重复实体：key 为实体 ID，value 为实体对象
        Map<String, KnowledgeEntity> entityMap = new HashMap<>();

        // 遍历所有实体关系
        for (EntityRelationDTO dto : relations) {
            // 处理 head 实体（关系的起点）
            String headId = generateEntityId(dto.getHead());
            KnowledgeEntity headEntity = entityMap.computeIfAbsent(headId,
                id -> createKnowledgeEntity(dto.getHead(), dto.getHeadType(), dto.getHeadDesc()));

            // 处理 tail 实体（关系的终点）
            String tailId = generateEntityId(dto.getTail());
            KnowledgeEntity tailEntity = entityMap.computeIfAbsent(tailId,
                id -> createKnowledgeEntity(dto.getTail(), dto.getTailType(), dto.getTailDesc()));

            // 创建实体关系对象
            EntityRelation relation = new EntityRelation();
            relation.setKey(dto.getRelation());                    // 关系类型
            relation.setDescription(dto.getRelation() + "关系");  // 关系描述
            relation.setTargetEntity(tailEntity);                  // 目标实体

            // 将关系添加到 head 实体的关系列表中
            headEntity.getRelations().add(relation);
        }

        // 使用 Repository 保存所有实体（包括关系）
        // Neo4j OGM 会自动保存关系
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
     *
     * <p>ID 生成规则：</p>
     * <ul>
     *   <li>前缀：添加 "ent:" 前缀标识这是实体</li>
     *   <li>转小写：将名称转为小写</li>
     *   <li>空格处理：将空格替换为下划线</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>
     * "人工智能" -> "ent:人工智能"
     * "Machine Learning" -> "ent:machine_learning"
     * </pre>
     *
     * @param name 实体名称
     * @return 实体唯一 ID
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
     *
     * @param name 实体名称
     * @param type 实体类型
     * @param description 实体描述
     * @return KnowledgeEntity 对象
     */
    private KnowledgeEntity createKnowledgeEntity(String name, String type, String description) {
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(generateEntityId(name));        // 生成唯一 ID
        entity.setName(name);                         // 设置名称
        entity.setType(type);                         // 设置类型
        entity.setDescription(description);           // 设置描述
        return entity;
    }

    /**
     * 建立 Chunk 与 Entity 之间的 MENTIONS 关系
     *
     * <p>此方法用于记录哪些实体在哪些文档分块中被提及。
     * 这种关系对于：</p>
     * <ul>
     *   <li>实体溯源：查找实体的来源文档</li>
     *   <li>社区报告：收集社区相关的文档内容</li>
     *   <li>上下文检索：提供实体的上下文信息</li>
     * </ul>
     *
     * <p>数据模型：</p>
     * <pre>
     * (chunk:Chunk)-[:MENTIONS]->(entity:Entity)
     * </pre>
     *
     * @param chunkId 文档分块 ID
     * @param entities 要建立关系的实体集合
     */
    private void linkChunkToEntities(String chunkId, Set<KnowledgeEntity> entities) {
        // 参数校验
        if (chunkId == null || chunkId.isBlank() || entities == null || entities.isEmpty()) {
            logger.debug("ChunkId 或实体集合为空，跳过建立关系");
            return;
        }

        try {
            // 步骤1: 查询 Chunk 节点
            Optional<Chunk> chunkOpt = chunkRepository.findById(chunkId);
            if (chunkOpt.isEmpty()) {
                logger.warn("未找到 Chunk 节点: {}", chunkId);
                return;
            }

            Chunk chunk = chunkOpt.get();

            // 步骤2: 添加实体到 Chunk 的 entities 列表
            // Chunk 实体中已经定义了 @Relationship(type = "MENTIONS")
            // 将实体添加到列表中并保存，Neo4j OGM 会自动创建关系
            for (KnowledgeEntity entity : entities) {
                if (!chunk.getEntities().contains(entity)) {
                    chunk.getEntities().add(entity);
                    logger.debug("建立 Chunk {} 与 Entity {} 的 MENTIONS 关系", chunkId, entity.getName());
                }
            }

            // 步骤3: 保存 Chunk（会自动创建 MENTIONS 关系）
            chunkRepository.save(chunk);

            logger.info("成功为 Chunk {} 建立与 {} 个实体的 MENTIONS 关系", chunkId, entities.size());

        } catch (Exception e) {
            logger.error("建立 Chunk {} 与实体关系失败: {}", chunkId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }
    /**
     * 为所有社区生成总结报告
     *
     * <p>此方法对每个社区执行以下操作：</p>
     * <ol>
     *   <li>查询社区中所有实体被提及的文档分块</li>
     *   <li>收集文档内容（限制最多30个分块、8000字符）</li>
     *   <li>调用 LLM 生成社区报告（标题、摘要、评分、关键词）</li>
     *   <li>将报告保存回 Community 节点</li>
     * </ol>
     *
     * <p>报告内容：</p>
     * <ul>
     *   <li>title：社区简短标题（5-10字）</li>
     *   <li>summary：社区详细摘要（50-100字）</li>
     *   <li>rating：置信度评分（0-10分）</li>
     *   <li>keywords：关键词列表</li>
     * </ul>
     *
     * <p>查询逻辑：</p>
     * <pre>
     * Community <-[:BELONGS_TO]- Entity <-[:MENTIONS]- Chunk
     * </pre>
     */
    private void generateCommunityReports() {
        // 步骤1: 加载社区报告生成提示词模板
        String promptTemplate = promptProperties.loadCommunityReportPrompt();

        // 步骤2: 查询所有社区及其相关文档内容
        Collection<Map<String, Object>> rows = neo4jClient.query(
                "MATCH (c:Community) " +
                        "CALL { " +  // 使用子查询避免笛卡尔积
                        "  WITH c " +
                        "  MATCH (c)<-[:BELONGS_TO]-(e:Entity)<-[:MENTIONS]-(ch:Chunk) " +
                        "  WHERE ch.content IS NOT NULL AND ch.content <> '' " +
                        "  RETURN collect(DISTINCT ch.content)[0..$limit] AS contents " +  // 限制最多30个分块
                        "} " +
                        "RETURN c.community_id AS communityId, c.level AS level, contents")
            .bindAll(Map.of("limit", COMMUNITY_REPORT_CHUNK_LIMIT))
            .fetch()
            .all();

        // 步骤3: 为每个社区生成报告
        for (Map<String, Object> row : rows) {
            String communityId = asString(row.get("communityId"));
            Integer level = asInteger(row.get("level"));
            List<String> contents = castStringList(row.get("contents"));

            // 跳过无效数据
            if (communityId == null || communityId.isBlank() || level == null || contents.isEmpty()) {
                continue;
            }

            // 拼接文档内容（使用分隔符）
            String inputText = String.join("\n\n---\n\n", contents);

            // 限制文本长度，避免 LLM 输入过长
            if (inputText.length() > COMMUNITY_REPORT_MAX_CHARS) {
                inputText = inputText.substring(0, COMMUNITY_REPORT_MAX_CHARS);
            }

            // 构建完整提示词
            String fullPrompt = promptTemplate.replace("{input_text}", inputText);

            try {
                // 调用 LLM 生成社区报告
                Message userMessage = new UserMessage(fullPrompt);
                Prompt prompt = new Prompt(List.of(userMessage));
                String response = chatModel.call(prompt).getResult().getOutput().getText();

                // 清理和解析 JSON 响应
                String cleanedJson = validateJsonResponse(response);
                CommunityReport report = parseCommunityReport(cleanedJson);

                // 如果解析失败，跳过此社区
                if (report == null) {
                    continue;
                }

                // 将报告保存回 Community 节点
                neo4jClient.query(
                        "MATCH (c:Community {community_id: $communityId, level: $level}) " +
                                "SET c.title = $title, " +
                                "    c.summary = $summary, " +
                                "    c.rating = $rating, " +
                                "    c.keywords = $keywords, " +
                                "    c.report_updated_at = $updatedAt")
                    .bindAll(Map.of(
                        "communityId", communityId,
                        "level", level,
                        "title", report.title,
                        "summary", report.summary,
                        "rating", report.rating,
                        "keywords", report.keywords,
                        "updatedAt", LocalDateTime.now().toString()))  // 记录更新时间
                    .run();

            } catch (Exception e) {
                // 捕获异常，避免某个社区的失败影响其他社区
                logger.warn("社区 {} 总结失败: {}", communityId, e.getMessage(), e);
            }
        }
    }

    /**
     * 解析社区报告 JSON
     *
     * <p>从 LLM 返回的 JSON 中提取以下字段：</p>
     * <ul>
     *   <li>title：社区标题</li>
     *   <li>summary：社区摘要</li>
     *   <li>rating：置信度评分</li>
     *   <li>keywords：关键词列表</li>
     * </ul>
     *
     * @param json LLM 返回的 JSON 字符串
     * @return CommunityReport 对象，如果解析失败则返回 null
     */
    private CommunityReport parseCommunityReport(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            String title = getText(node.get("title"));
            String summary = getText(node.get("summary"));
            Double rating = node.hasNonNull("rating") ? node.get("rating").asDouble() : null;

            // 解析关键词列表
            List<String> keywords = new ArrayList<>();
            if (node.has("keywords") && node.get("keywords").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : node.get("keywords")) {
                    String value = getText(item);
                    if (value != null && !value.isBlank()) {
                        keywords.add(value);
                    }
                }
            }

            return new CommunityReport(title, summary, rating, keywords);
        } catch (Exception e) {
            logger.warn("解析社区总结 JSON 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 JsonNode 中提取文本值
     *
     * @param node JsonNode 对象
     * @return 文本值，如果节点为 null 或空则返回 null
     */
    private String getText(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 将对象转换为字符串
     *
     * @param value 任意对象
     * @return 字符串形式，如果对象为 null 则返回 null
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 将对象转换为整数
     *
     * @param value 任意对象（支持 Number 或 String）
     * @return 整数值，如果转换失败则返回 null
     */
    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将对象转换为字符串列表
     *
     * @param value 任意对象（期望是 List 类型）
     * @return 字符串列表，如果转换失败则返回空列表
     */
    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> results = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    results.add(item.toString());
                }
            }
            return results;
        }
        return Collections.emptyList();
    }

    /**
     * 社区报告内部类
     *
     * <p>用于封装社区报告的数据结构。
     * 使用不可变对象模式，所有字段都是 final 的。</p>
     */
    private static class CommunityReport {
        /**
         * 社区标题（5-10字）
         */
        private final String title;

        /**
         * 社区摘要（50-100字）
         */
        private final String summary;

        /**
         * 置信度评分（0-10分）
         */
        private final Double rating;

        /**
         * 关键词列表
         */
        private final List<String> keywords;

        /**
         * 构造函数
         *
         * @param title 社区标题
         * @param summary 社区摘要
         * @param rating 置信度评分
         * @param keywords 关键词列表
         */
        private CommunityReport(String title, String summary, Double rating, List<String> keywords) {
            this.title = title;
            this.summary = summary;
            this.rating = rating;
            this.keywords = keywords == null ? Collections.emptyList() : keywords;
        }
    }
}
