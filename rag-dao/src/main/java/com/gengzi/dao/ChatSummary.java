package com.gengzi.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * 会话摘要表
 */
@Getter
@Setter
@Entity
@Table(name = "chat_summaries", schema = "rag_db", indexes = {
        @Index(name = "idx_conversation_id", columnList = "conversation_id"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class ChatSummary {
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 关联会话ID (支持UUID/自定义ID)
     */
    @Size(max = 64)
    @NotNull
    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    /**
     * LLM生成的总结内容
     */
    @NotNull
    @Lob
    @Column(name = "summary_content", nullable = false)
    private String summaryContent;

    /**
     * 摘要覆盖的起始消息ID
     */
    @Size(max = 64)
    @NotNull
    @Column(name = "start_message_id", nullable = false, length = 64)
    private String startMessageId;

    /**
     * 摘要覆盖的结束消息ID
     */
    @Size(max = 64)
    @NotNull
    @Column(name = "end_message_id", nullable = false, length = 64)
    private String endMessageId;

    /**
     * 摘要消耗的Token数量
     */
    @ColumnDefault("'0'")
    @Column(name = "token_count", columnDefinition = "int UNSIGNED")
    private Long tokenCount;

    /**
     * 生成摘要的模型名称
     */
    @Size(max = 50)
    @Column(name = "model_name", length = 50)
    private String modelName;

    /**
     * 创建时间
     */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * 更新时间
     */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at")
    private Instant updatedAt;

}