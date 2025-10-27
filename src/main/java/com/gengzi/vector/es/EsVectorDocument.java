package com.gengzi.vector.es;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.ai.content.Media;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * 包含所有自定义字段的 Elasticsearch 向量文档实体类
 */
@Data
public class EsVectorDocument {

    private String chunkId;

    // 1. 源文档标识字段
    @JsonProperty("doc_id") // Elasticsearch 中的字段名（小写+下划线，符合 ES 命名规范）
    private String docId; // 源文档唯一ID（对应原需求的 doc_id）

    // 1. 源文档标识字段
    @JsonProperty("f_id") // Elasticsearch 中的字段名（小写+下划线，符合 ES 命名规范）
    private String fId; // 源文档唯一ID（对应原需求的 doc_id）

    @JsonProperty("kb_id")
    private String kbId; // 知识库ID（对应原需求的 kb_id）

    // 2. 内容与元数据字段（原有的 content 保留，新增扩展字段）
    @JsonProperty("content")
    private String content; // 原文档内容（保留，确保兼容性）

    @JsonProperty("docnm_kwd")
    private String docnmKwd; // 源文件名称（含扩展名，对应 docnm_kwd）

    @JsonProperty("title_tks")
    private String titleTks; // 标题分词结果（空格分隔，对应 title_tks）

    @JsonProperty("title_sm_tks")
    private String titleSmTks; // 标题简化分词结果（对应 title_sm_tks）

    @JsonProperty("content_with_weight")
    private String contentWithWeight; // 带权重的内容文本（对应 content_with_weight）

    @JsonProperty("content_ltks")
    private String contentLtks; // 内容全量分词结果（对应 content_ltks）

    @JsonProperty("content_sm_ltks")
    private String contentSmLtks; // 内容简化分词结果（对应 content_sm_ltks）

    @JsonProperty("doc_type_kwd")
    private String docTypeKwd; // 文档类型（如 image/text，对应 doc_type_kwd）

    // 3. 位置相关字段（存储为字符串，后续可解析为数组）
    @JsonProperty("page_num_int")
    private String pageNumInt; // 页码信息（如"[5]"，对应 page_num_int）

    @JsonProperty("position_int")
    private int positionInt; // 位置坐标（如"[[5,1144,1851,657,973]]"，对应 position_int）

    @JsonProperty("top_int")
    private int topInt; // 顶部位置坐标（如"[657]"，对应 top_int）

    // 4. 时间字段
    @JsonProperty("create_time")
    private String createTime; // 创建时间（格式化字符串，对应 create_time）

    @JsonProperty("create_timestamp_flt")
    private float createTimestampFlt; // 创建时间戳（浮点型字符串，对应 create_timestamp_flt）

    // 5. 扩展字段
    @JsonProperty("img_id")
    private String imgId; // 图片ID（仅图片类型文档有效，对应 img_id）


    @JsonProperty("q_1024_vec")
    private float[] q1024Vec; // 1024维向量（对应 q_1024_vec，注意 ES 中 dense_vector 需指定维度）

    @JsonProperty("available_int")
    private Integer availableInt;

    // 7. 元数据（保留原有的 metadata，确保兼容性）
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @Nullable
    private Double score;

    private Media media;


    // （注意：字段类型需与 Elasticsearch 映射一致，如 q1024_vec 必须是 float[]，避免使用 List<Float>）




}