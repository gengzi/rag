package com.gengzi.agentteams.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamTask {

    // 任务唯一标识
    private final String id;
    // 任务标题
    private final String title;
    // 任务详细说明
    private final String description;
    // 前置依赖任务ID列表
    private final List<String> dependencyTaskIds;
    // 当前状态：待处理/进行中/已完成
    private TaskStatus status;
    // 当前执行人（teammateId）
    private String assigneeId;
    // 执行结果（通常是模型输出）
    private String result;
    // 创建时间
    private final Instant createdAt;
    // 最后更新时间
    private Instant updatedAt;

    public TeamTask(String title, String description, List<String> dependencyTaskIds, String assigneeId) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.dependencyTaskIds = new ArrayList<>(dependencyTaskIds);
        this.status = TaskStatus.PENDING;
        this.assigneeId = assigneeId;
        this.result = null;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getDependencyTaskIds() {
        return dependencyTaskIds;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        // 状态变化时同步更新时间，便于追踪任务流转
        this.updatedAt = Instant.now();
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
        // 指派变化时同步更新时间
        this.updatedAt = Instant.now();
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
        // 写入结果时同步更新时间
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
