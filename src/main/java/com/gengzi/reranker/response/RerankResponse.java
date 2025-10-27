package com.gengzi.reranker.response;

import lombok.Data;
import java.util.List;

/**
 * Reranker 模型的响应结果
 */
@Data
public class RerankResponse {
    // 响应唯一标识
    private String id;
    
    // 重排序后的结果列表
    private List<Result> results;
    
    // 令牌使用统计
    private Tokens tokens;

    /**
     * 单个候选文本的重排序结果
     */
    @Data
    public static class Result {
        // 文档内容
        private Document document;
        
        // 原始候选文本的索引（与输入顺序对应）
        private Integer index;
        
        // 相关性分数（越高表示与查询越相关）
        private Double relevance_score;
    }

    /**
     * 文档内容实体
     */
    @Data
    public static class Document {
        // 文档文本内容
        private String text;
    }

    /**
     * 令牌使用统计
     */
    @Data
    public static class Tokens {
        // 输入令牌数量
        private Integer input_tokens;
        
        // 输出令牌数量
        private Integer output_tokens;
    }
}