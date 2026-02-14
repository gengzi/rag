package com.gengzi.agentteams.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamTask {

    private final String id;
    private final String title;
    private final String description;
    private final List<String> dependencyTaskIds;
    private TaskStatus status;
    private String assigneeId;
    private String result;
    private final Instant createdAt;
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
        this.updatedAt = Instant.now();
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
        this.updatedAt = Instant.now();
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
