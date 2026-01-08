package com.gengzi.controller;

import com.gengzi.model.dto.GraphQueryRequest;
import com.gengzi.model.dto.GraphQueryResponse;
import com.gengzi.service.graph.GraphRetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识图谱检索控制器
 *
 * <p>提供基于知识图谱的文档检索 REST API，支持智能检索模式：</p>
 * <ul>
 *   <li>LOCAL 模式：基于实体关系的局部检索（遍历实体邻居）</li>
 *   <li>GLOBAL 模式：基于社区的全文检索（匹配社区关键词）</li>
 *   <li>自动回退：首选模式无结果时自动切换到备选模式</li>
 * </ul>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>接收用户查询问题</li>
 *   <li>调用 LLM 分析查询，提取实体和关键词</li>
 *   <li>根据查询类型选择最优检索策略</li>
 *   <li>返回匹配的文档分块及其关联信息</li>
 * </ul>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 */
@Tag(name = "Graph Retrieval", description = "Retrieve chunks from the knowledge graph.")
@RestController
@RequestMapping("/api/graph")
public class GraphRetrievalController {

    /**
     * 知识图谱检索服务
     * 负责执行实际的图检索逻辑
     */
    private final GraphRetrievalService graphRetrievalService;

    /**
     * 构造函数，通过依赖注入初始化服务
     *
     * @param graphRetrievalService 图谱检索服务实例
     */
    public GraphRetrievalController(GraphRetrievalService graphRetrievalService) {
        this.graphRetrievalService = graphRetrievalService;
    }

    /**
     * 根据用户问题检索相关的文档分块
     *
     * <p>API 端点：POST /api/graph/retrieve</p>
     *
     * <p>请求参数：</p>
     * <pre>
     * {
     *   "question": "用户查询问题",
     *   "neighborDepth": 2,    // 可选，邻居遍历深度（1-2），默认 2
     *   "limit": 50            // 可选，返回结果数量限制，默认 50
     * }
     * </pre>
     *
     * <p>响应结果：</p>
     * <pre>
     * {
     *   "question": "用户查询问题",
     *   "searchType": "LOCAL" | "GLOBAL",
     *   "entities": ["实体1", "实体2"],
     *   "keywords": ["关键词1", "关键词2"],
     *   "chunks": [
     *     {
     *       "chunkId": "chunk_id",
     *       "content": "分块内容",
     *       "entityNames": ["实体1", "实体2"],
     *       "communityIds": ["L2:123"]
     *     }
     *   ]
     * }
     * </pre>
     *
     * <p>检索模式：</p>
     * <ul>
     *   <li>LOCAL：基于实体关系，适合结构化查询</li>
     *   <li>GLOBAL：基于社区关键词，适合主题性查询</li>
     * </ul>
     *
     * @param request 图谱查询请求对象
     * @return 图谱查询响应对象，包含匹配的分块列表
     */
    @Operation(summary = "Retrieve chunks by question")
    @PostMapping("/retrieve")
    public ResponseEntity<GraphQueryResponse> retrieve(@RequestBody GraphQueryRequest request) {
        // 参数校验：问题不能为空
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // 调用服务层执行检索
        GraphQueryResponse response = graphRetrievalService.retrieveChunks(
            request.getQuestion(),
            request.getNeighborDepth(),
            request.getLimit()
        );

        return ResponseEntity.ok(response);
    }
}
