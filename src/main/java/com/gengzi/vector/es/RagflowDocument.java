package com.gengzi.vector.es;

import lombok.Data;
import java.util.List;

/**
 * 对应ragflow文档结构的实体类
 * 包含ragflow生成的所有字段，支持序列化与反序列化
 */
@Data
public class RagflowDocument {
    // 核心标识字段
    private String _id;                  // ES文档唯一ID
    private String doc_id;               // 源文档唯一ID
    private String kb_id;                // 知识库ID

    // 内容与元数据字段
    private String docnm_kwd;            // 源文件名称（含扩展名）
    private String title_tks;            // 标题分词结果（空格分隔）
    private String title_sm_tks;         // 标题简化分词结果
    private String content_with_weight;  // 带权重的内容文本
    private String content_ltks;         // 内容全量分词结果
    private String content_sm_ltks;      // 内容简化分词结果
    private String doc_type_kwd;         // 文档类型（如image/text）
    
    // 位置相关字段（存储为字符串，需解析为数组）
    private String page_num_int;         // 页码信息（如"[5]"）
    private String position_int;         // 位置坐标（如"[[5,1144,1851,657,973]]"）
    private String top_int;              // 顶部位置坐标（如"[657]"）
    
    // 时间字段
    private String create_time;          // 创建时间（格式化字符串）
    private String create_timestamp_flt; // 创建时间戳（浮点型字符串）
    
    // 扩展字段
    private String img_id;               // 图片ID（仅图片类型文档有效）
    private String q_1024_vec;           // 1024维向量（字符串形式，如["-0.0085...", ...]）

    /**
     * 解析 page_num_int 为整数列表
     * 示例："[5]" → [5]
     */
    public List<Integer> parsePageNum() {
        return parseIntegerList(page_num_int);
    }

    /**
     * 解析 top_int 为整数列表
     * 示例："[657]" → [657]
     */
    public List<Integer> parseTopInt() {
        return parseIntegerList(top_int);
    }

    /**
     * 解析 position_int 为二维整数列表
     * 示例："[[5,1144,1851,657,973]]" → [[5,1144,1851,657,973]]
     */
    public List<List<Integer>> parsePositionInt() {
        return parseTwoDimensionalIntegerList(position_int);
    }

    /**
     * 解析 q_1024_vec 为浮点型数组（向量）
     * 示例：["-0.0085...", ...] → float[]
     */
    public float[] parseVector() {
        if (q_1024_vec == null || q_1024_vec.isEmpty()) {
            return new float[0];
        }
        // 去除前后的[]和引号，按逗号分割
        String cleaned = q_1024_vec.replaceAll("[\\[\\]\"]", "").trim();
        String[] parts = cleaned.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    // 工具方法：将字符串解析为整数列表（如"[1,2,3]" → [1,2,3]）
    private List<Integer> parseIntegerList(String str) {
        // 实际实现需使用JSON解析库（如Jackson），此处简化逻辑
        if (str == null || str.isEmpty()) {
            return List.of();
        }
        // 示例：使用Jackson的ObjectMapper解析
        // return new ObjectMapper().readValue(str, new TypeReference<List<Integer>>() {});
        return List.of(); // 占位，需替换为实际解析逻辑
    }

    // 工具方法：将字符串解析为二维整数列表
    private List<List<Integer>> parseTwoDimensionalIntegerList(String str) {
        // 实际实现需使用JSON解析库，此处简化逻辑
        if (str == null || str.isEmpty()) {
            return List.of();
        }
        // 示例：return new ObjectMapper().readValue(str, new TypeReference<List<List<Integer>>>() {});
        return List.of(); // 占位，需替换为实际解析逻辑
    }
}
