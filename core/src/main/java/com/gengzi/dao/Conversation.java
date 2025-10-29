package com.gengzi.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 存储对话记录的核心表，包含单轮对话内容、关联会话及用户信息
 */
@Getter
@Setter
@Entity
@Table(name = "conversation", schema = "rag_db", indexes = {
        @Index(name = "conversation_create_time", columnList = "create_time"),
        @Index(name = "conversation_create_date", columnList = "create_date"),
        @Index(name = "conversation_update_time", columnList = "update_time"),
        @Index(name = "conversation_update_date", columnList = "update_date"),
        @Index(name = "conversation_name", columnList = "name"),
        @Index(name = "conversation_user_id", columnList = "user_id")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Conversation {
    /**
     * 对话记录唯一标识（主键），通常为UUID或雪花ID
     */
    @Id
    @Size(max = 64)
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * 创建时间戳（毫秒级），用于时间排序和计算
     */
    @Column(name = "create_time")
    private Long createTime;

    /**
     * 创建日期时间（YYYY-MM-DD HH:MM:SS），用于直观展示创建时间
     */
    @Column(name = "create_date")
    private LocalDateTime createDate;

    /**
     * 最后更新时间戳（毫秒级），记录数据修改时间
     */
    @Column(name = "update_time")
    private Long updateTime;

    /**
     * 最后更新日期时间（YYYY-MM-DD HH:MM:SS），直观展示更新时间
     */
    @Column(name = "update_date")
    private LocalDateTime updateDate;

//    /**
//     * 会话ID，关联同一轮对话的所有记录（多轮对话归属标识）
//     */
//    @Size(max = 32)
//    @NotNull
//    @Column(name = "dialog_id", nullable = false, length = 32)
//    private String dialogId;

    /**
     * 对话名称/标题，用于快速识别对话主题（如“订单咨询”）
     */
    @Size(max = 255)
    @Column(name = "name")
    private String name;

    /**
     * 对话消息内容，存储用户提问、AI回复等完整文本（支持超大文本）
     */
    @Lob
    @Column(name = "message")
    private String message;

    /**
     * 参考信息，存储对话关联的引用数据（如RAG知识库片段、文档ID等）
     */
    @Lob
    @Column(name = "reference")
    private String reference;

    /**
     * 用户ID，关联对话所属用户（用于权限控制和用户数据隔离）
     */
    @Size(max = 255)
    @Column(name = "user_id")
    private String userId;

    @Size(max = 64)
    @NotNull
    @Column(name = "knowledgebase_id", nullable = false, length = 64)
    private String knowledgebaseId;

}