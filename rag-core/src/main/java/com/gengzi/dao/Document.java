package com.gengzi.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "document", schema = "rag_db", indexes = {
        @Index(name = "document_create_time", columnList = "create_time"),
        @Index(name = "document_create_date", columnList = "create_date"),
        @Index(name = "document_update_time", columnList = "update_time"),
        @Index(name = "document_update_date", columnList = "update_date"),
        @Index(name = "document_kb_id", columnList = "kb_id"),
        @Index(name = "document_type", columnList = "type"),
        @Index(name = "document_created_by", columnList = "created_by"),
        @Index(name = "document_name", columnList = "name"),
        @Index(name = "document_location", columnList = "location"),
        @Index(name = "document_size", columnList = "size"),
        @Index(name = "document_token_num", columnList = "token_num"),
        @Index(name = "document_chunk_num", columnList = "chunk_num"),
        @Index(name = "document_progress", columnList = "progress"),
        @Index(name = "document_process_begin_at", columnList = "process_begin_at"),
        @Index(name = "document_suffix", columnList = "suffix"),
        @Index(name = "document_run", columnList = "run"),
        @Index(name = "document_status", columnList = "status")
})
public class Document {
    /**
     * 文档唯一标识ID
     */
    @Id
    @Size(max = 64)
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * 创建时间戳（毫秒级）
     */
    @Column(name = "create_time")
    private Long createTime;

    /**
     * 创建日期时间（格式化）
     */
    @Column(name = "create_date")
    private Instant createDate;

    /**
     * 更新时间戳（毫秒级）
     */
    @Column(name = "update_time")
    private Long updateTime;

    /**
     * 更新日期时间（格式化）
     */
    @Column(name = "update_date")
    private Instant updateDate;

    /**
     * 文档缩略图URL
     */
    @Lob
    @Column(name = "thumbnail")
    private String thumbnail;

    /**
     * 关联的知识库ID（外键关联knowledgebase表）
     */
    @Size(max = 256)
    @NotNull
    @Column(name = "kb_id", nullable = false, length = 256)
    private String kbId;

    /**
     * 文档内容类型（如：文本、表格、图片、混合类型等）
     */
    @Size(max = 256)
    @NotNull
    @Column(name = "type", nullable = false, length = 256)
    private String type;

    /**
     * 上传者/创建者用户ID
     */
    @Size(max = 32)
    @NotNull
    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    /**
     * 文档名称
     */
    @Size(max = 255)
    @Column(name = "name")
    private String name;

    /**
     * 文档存储路径或访问地址
     */
    @Size(max = 255)
    @Column(name = "location")
    private String location;

    /**
     * 文档大小（单位：字节）
     */
    @NotNull
    @Column(name = "size", nullable = false)
    private Long size;

    /**
     * 文档转换后的token总数量
     */
    @NotNull
    @Column(name = "token_num", nullable = false)
    private Integer tokenNum;

    /**
     * 文档拆分后的片段数量
     */
    @NotNull
    @Column(name = "chunk_num", nullable = false)
    private Integer chunkNum;

    /**
     * 文档处理进度（0-100，百分比）
     */
    @NotNull
    @Column(name = "progress", nullable = false)
    private Float progress;

    /**
     * 进度描述信息（如：解析中、拆分完成等）
     */
    @Lob
    @Column(name = "progress_msg")
    private String progressMsg;

    /**
     * 文档处理开始时间
     */
    @Column(name = "process_begin_at")
    private Instant processBeginAt;

    /**
     * 文档处理耗时（单位：秒）
     */
    @NotNull
    @Column(name = "process_duration", nullable = false)
    private Float processDuration;

    /**
     * 文档元数据（扩展字段，JSON格式，如作者、创建时间等）
     */
    @Lob
    @Column(name = "meta_fields")
    private String metaFields;

    /**
     * 文档后缀名（如：pdf、docx、txt等）
     */
    @Size(max = 32)
    @NotNull
    @Column(name = "suffix", nullable = false, length = 32)
    private String suffix;

    /**
     * 是否执行处理（0-不执行，1-执行）
     */
    @Size(max = 1)
    @Column(name = "run", length = 1)
    private String run;

    /**
     * 文档状态（如：0-未处理，1-处理中，2-处理完成，3-处理失败）
     */
    @Size(max = 1)
    @Column(name = "status", length = 1)
    private String status;

}