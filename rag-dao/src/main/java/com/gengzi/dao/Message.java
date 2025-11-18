package com.gengzi.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * 对话消息表，结构化上下文以内嵌注释形式融合在 content 中
 */
@Getter
@Setter
@Entity
@Table(name = "messages", schema = "rag_db", indexes = {
        @Index(name = "idx_conv_time", columnList = "conversation_id, created_time")
})
public class Message {
    /**
     * 消息唯一ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 所属对话ID
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * 角色：user / assistant / agent 等
     */
    @Size(max = 32)
    @NotNull
    @Column(name = "message_role", nullable = false, length = 32)
    private String messageRole;

    /**
     * 完整消息内容，含用户可见文本 + 内嵌结构化元数据（如 <!-- AGENT_META {...} -->）
     */
    @NotNull
    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    /**
     * 创建时间
     */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_time")
    private Instant createdTime;

}