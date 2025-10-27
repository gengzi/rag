package com.gengzi.dao;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 系统用户信息表，存储用户基础信息、认证状态、偏好设置及权限数据
 */
@Getter
@Setter
@Entity
@Table(name = "user", schema = "rag_db", indexes = {
        @Index(name = "user_create_time", columnList = "create_time"),
        @Index(name = "user_create_date", columnList = "create_date"),
        @Index(name = "user_update_time", columnList = "update_time"),
        @Index(name = "user_update_date", columnList = "update_date"),
        @Index(name = "user_access_token", columnList = "access_token"),
        @Index(name = "user_nickname", columnList = "nickname"),
        @Index(name = "user_password", columnList = "password"),
        @Index(name = "user_email", columnList = "username"),
        @Index(name = "user_language", columnList = "language"),
        @Index(name = "user_color_schema", columnList = "color_schema"),
        @Index(name = "user_timezone", columnList = "timezone"),
        @Index(name = "user_last_login_time", columnList = "last_login_time"),
        @Index(name = "user_is_authenticated", columnList = "is_authenticated"),
        @Index(name = "user_is_active", columnList = "is_active"),
        @Index(name = "user_is_anonymous", columnList = "is_anonymous"),
        @Index(name = "user_login_channel", columnList = "login_channel"),
        @Index(name = "user_status", columnList = "status"),
        @Index(name = "user_is_superuser", columnList = "is_superuser")
}, uniqueConstraints = {
        @UniqueConstraint(name = "username_UNIQUE", columnNames = {"username"})
})
public class User {
    /**
     * 用户唯一标识（主键），通常为UUID或雪花ID，确保分布式环境下唯一
     */
    @Id
    @Size(max = 64)
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * 记录创建时间戳（毫秒级），用于精确时间排序和跨时区统一计算
     */
    @Column(name = "create_time")
    private Long createTime;

    /**
     * 记录创建时间（带时区的日期时间），格式如YYYY-MM-DD HH:MM:SS，用于人类可读展示
     */
    @Column(name = "create_date")
    private Instant createDate;

    /**
     * 记录最后更新时间戳（毫秒级），用于判断数据新鲜度和更新追踪
     */
    @Column(name = "update_time")
    private Long updateTime;

    /**
     * 记录最后更新时间（带时区的日期时间），用于展示最近修改时间
     */
    @Column(name = "update_date")
    private Instant updateDate;

    /**
     * 用户登录后生成的访问令牌，用于接口鉴权，失效后需重新获取
     */
    @Size(max = 255)
    @Column(name = "access_token")
    private String accessToken;

    /**
     * 用户昵称（显示用），可修改，非唯一标识，需过滤敏感词
     */
    @Size(max = 100)
    @Column(name = "nickname", length = 100)
    private String nickname;

    /**
     * 加密后的用户密码（如BCrypt/SHA256哈希值），严禁存储明文
     */
    @Size(max = 255)
    @Column(name = "password")
    private String password;

    /**
     * 用户名唯一
     */
    @Size(max = 255)
    @NotNull
    @Column(name = "username", nullable = false)
    private String username;

    /**
     * 用户头像图片URL或Base64编码，text类型支持较长内容存储
     */
    @Lob
    @Column(name = "avatar")
    private String avatar;

    /**
     * 用户选择的系统语言，如zh-CN（简体中文）、en-US（美式英语），用于国际化
     */
    @Size(max = 32)
    @Column(name = "language", length = 32)
    private String language;

    /**
     * 用户偏好的界面主题，如light（浅色）、dark（深色）、blue（蓝色主题）
     */
    @Size(max = 32)
    @Column(name = "color_schema", length = 32)
    private String colorSchema;

    /**
     * 用户所在时区，如Asia/Shanghai（上海）、UTC，用于本地化时间展示
     */
    @Size(max = 64)
    @Column(name = "timezone", length = 64)
    private String timezone;

    /**
     * 用户最后一次成功登录的时间，用于安全审计和活跃度统计
     */
    @Column(name = "last_login_time")
    private Instant lastLoginTime;

    /**
     * 是否完成身份认证（如邮箱验证），取值Y（是）/N（否），控制功能权限
     */
    @Size(max = 1)
    @Column(name = "is_authenticated", length = 1)
    private String isAuthenticated;

    /**
     * 账户是否激活可用，取值Y（正常）/N（禁用），禁用后无法登录
     */
    @Size(max = 1)
    @Column(name = "is_active", length = 1)
    private String isActive;

    /**
     * 是否为匿名用户，取值Y（是）/N（否），区分注册用户和临时访问者
     */
    @Size(max = 1)
    @Column(name = "is_anonymous", length = 1)
    private String isAnonymous;

    /**
     * 用户登录渠道，如password（密码登录）、wechat（微信）、github（第三方）
     */
    @Size(max = 255)
    @Column(name = "login_channel")
    private String loginChannel;

    /**
     * 账户状态扩展（细分状态），如0-待审核、1-正常、2-冻结（需结合业务定义）
     */
    @Size(max = 1)
    @Column(name = "status", length = 1)
    private String status;

    /**
     * 是否为超级管理员，取值1（是）/0（否），控制系统最高权限
     */
    @NotNull
    @Column(name = "is_superuser", nullable = false)
    private Boolean isSuperuser;

    /**
     * 用户绑定的知识库ID列表，逗号分隔
     */
    @NotNull
    @Lob
    @Column(name = "knowledge_ids", nullable = false)
    private String knowledgeIds;

}