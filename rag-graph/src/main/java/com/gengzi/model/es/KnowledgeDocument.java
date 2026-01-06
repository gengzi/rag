package com.gengzi.model.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

/**
 * 对应索引：rag_store_new
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "rag_store_new")
public class KnowledgeDocument {

    @Id
    @Field(type = FieldType.Text)
    private String id;

    // --- 核心内容字段 ---

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(name = "content_ltks", type = FieldType.Text, analyzer = "whitespace")
    private String contentLtks;

    @Field(name = "content_sm_ltks", type = FieldType.Text, analyzer = "whitespace")
    private String contentSmLtks;

    // --- 时间与数值 ---

    @Field(name = "create_time", type = FieldType.Date, format = DateFormat.time, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Field(name = "create_timestamp_flt", type = FieldType.Float)
    private Float createTimestampFlt;

    // --- 关键字字段 ---

    @Field(name = "doc_id", type = FieldType.Keyword)
    private String docId;

    @Field(name = "doc_type_kwd", type = FieldType.Keyword)
    private String docTypeKwd;

    @Field(name = "docnm_kwd", type = FieldType.Keyword)
    private String docnmKwd;

    @Field(name = "img_id", type = FieldType.Keyword)
    private String imgId;

    @Field(name = "kb_id", type = FieldType.Keyword)
    private String kbId;

    // --- 向量字段（用于语义检索） ---

    @Field(name = "q_1024_vec", type = FieldType.Dense_Vector, dims = 1024)
    private float[] q1024Vec;

    // --- 标题分词 ---

    @Field(name = "title_sm_tks", type = FieldType.Text, analyzer = "whitespace")
    private String titleSmTks;

    @Field(name = "title_tks", type = FieldType.Text, analyzer = "whitespace")
    private String titleTks;

    // --- 不参与索引的字段 ---

    @Field(name = "page_num_int", type = FieldType.Keyword, index = false)
    private String pageNumIntNoIndex;

    @Field(name = "pageNumInt", type = FieldType.Text)
    private String pageNumIntText;

    @Field(name = "position_int", type = FieldType.Keyword, index = false)
    private String positionInt;

    @Field(name = "top_int", type = FieldType.Keyword, index = false)
    private String topInt;

    // --- Metadata 结构 ---

    @Field(type = FieldType.Object)
    private Metadata metadata;

    /**
     * 文档元数据。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {

        @Field(type = FieldType.Text)
        private String chunkContentType;

        @Field(type = FieldType.Text)
        private String contentType;

        @Field(type = FieldType.Long)
        private Long convertedTimestamp;

        @Field(type = FieldType.Text)
        private String createdAt;

        @Field(type = FieldType.Text)
        private String documentId;

        @Field(type = FieldType.Text)
        private String documentName;

        @Field(type = FieldType.Text)
        private String documentType;

        @Field(name = "excerpt_keywords", type = FieldType.Text)
        private String excerptKeywords;

        @Field(type = FieldType.Text)
        private String fileId;

        @Field(type = FieldType.Boolean)
        private Boolean hasInputImage;

        @Field(name = "image_resource", type = FieldType.Text)
        private String imageResource;

        @Field(type = FieldType.Text)
        private String inputImageBase64;

        @Field(type = FieldType.Boolean)
        private Boolean isParagraphEnd;

        @Field(type = FieldType.Boolean)
        private Boolean isParagraphStart;

        @Field(type = FieldType.Boolean)
        private Boolean isValid;

        @Field(type = FieldType.Text)
        private String kbId;

        @Field(name = "next_section_summary", type = FieldType.Text)
        private String nextSectionSummary;

        @Field(type = FieldType.Long)
        private Long outputImageCount;

        @Field(type = FieldType.Text)
        private String outputImageNames;

        @Field(type = FieldType.Long)
        private Long pageNumber;

        @Field(type = FieldType.Text)
        private String pageRange;

        @Field(name = "prev_section_summary", type = FieldType.Text)
        private String prevSectionSummary;

        @Field(type = FieldType.Text)
        private String requestLogId;

        @Field(name = "section_summary", type = FieldType.Text)
        private String sectionSummary;

        @Field(type = FieldType.Text)
        private String sourceFileUrl;
    }
}
