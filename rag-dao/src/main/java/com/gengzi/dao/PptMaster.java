package com.gengzi.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/**
 * PPT母版信息表
 */
@Getter
@Setter
@Entity
@Table(name = "ppt_master", schema = "rag_db", uniqueConstraints = {
        @UniqueConstraint(name = "name_UNIQUE", columnNames = {"name"})
})
public class PptMaster {
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 原始PPT文件唯一标识（如MD5或业务ID）
     */
    @Size(max = 64)
    @NotNull
    @Column(name = "file_id", nullable = false, length = 64)
    private String fileId;

    /**
     * 母版名称
     */
    @Size(max = 255)
    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * ppt子版json样式
     */
    @NotNull
    @Lob
    @Column(name = "ppt_layout", nullable = false)
    private String pptLayout;

    /**
     * 创建时间
     */
    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_time", nullable = false)
    private Instant createdTime;

    /**
     * 更新时间
     */
    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_time", nullable = false)
    private Instant updatedTime;

}