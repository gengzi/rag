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
@Table(name = "knowledgebase", schema = "rag_db", indexes = {
        @Index(name = "knowledgebase_create_time", columnList = "create_time"),
        @Index(name = "knowledgebase_create_date", columnList = "create_date"),
        @Index(name = "knowledgebase_update_time", columnList = "update_time"),
        @Index(name = "knowledgebase_update_date", columnList = "update_date"),
        @Index(name = "knowledgebase_name", columnList = "name"),
        @Index(name = "knowledgebase_created_by", columnList = "created_by"),
        @Index(name = "knowledgebase_status", columnList = "status")
})
public class Knowledgebase {
    /**
     * 知识库唯一标识ID
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
     * 知识库头像URL
     */
    @Lob
    @Column(name = "avatar")
    private String avatar;

    /**
     * 知识库名称
     */
    @Size(max = 128)
    @NotNull
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * 知识库主要使用的语言
     */
    @Size(max = 32)
    @Column(name = "language", length = 32)
    private String language;

    /**
     * 知识库描述信息
     */
    @Lob
    @Column(name = "description")
    private String description;

    /**
     * 创建人用户ID
     */
    @Size(max = 32)
    @NotNull
    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    /**
     * 知识库包含的文档数量
     */
    @NotNull
    @Column(name = "doc_num", nullable = false)
    private Integer docNum;

    /**
     * 知识库总token数量
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
     * 状态（如：0-禁用，1-启用）
     */
    @Size(max = 1)
    @Column(name = "status", length = 1)
    private String status;

}