package com.gengzi.agentteams.api;

import com.gengzi.agentteams.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public class TeamStateResponse {

    @Schema(description = "团队ID", example = "5dbd90ef-1d42-4928-af76-6f0c0734ca00")
    private String id;
    @Schema(description = "团队名称", example = "Market Analysis Team")
    private String name;
    @Schema(description = "团队总目标", example = "调研北美 AI Agent 产品机会并输出结论")
    private String objective;
    @Schema(description = "团队创建时间", example = "2026-02-14T05:26:56.806Z")
    private Instant createdAt;
    @ArraySchema(arraySchema = @Schema(description = "团队成员列表"))
    private List<TeammateView> teammates;
    @ArraySchema(arraySchema = @Schema(description = "任务列表"))
    private List<TaskView> tasks;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<TeammateView> getTeammates() {
        return teammates;
    }

    public void setTeammates(List<TeammateView> teammates) {
        this.teammates = teammates;
    }

    public List<TaskView> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskView> tasks) {
        this.tasks = tasks;
    }

    public static class TeammateView {
        @Schema(description = "成员ID", example = "2ef75da3-2a92-4eb8-a5f7-c37cb13db341")
        private String id;
        @Schema(description = "成员名称", example = "Alice")
        private String name;
        @Schema(description = "成员角色", example = "Researcher")
        private String role;
        @Schema(description = "成员实际使用模型（来自系统配置）", example = "claude-opus-4-6-thinking")
        private String model;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class TaskView {
        @Schema(description = "任务ID", example = "9b7dd9b8-2fbe-4f6e-8a30-cc3652b02db2")
        private String id;
        @Schema(description = "任务标题", example = "收集竞品信息")
        private String title;
        @Schema(description = "任务描述", example = "列出 5 个竞品并总结定位")
        private String description;
        @ArraySchema(arraySchema = @Schema(description = "依赖任务ID列表"))
        private List<String> dependencies;
        @Schema(description = "任务状态", example = "IN_PROGRESS")
        private TaskStatus status;
        @Schema(description = "指派成员ID", example = "2ef75da3-2a92-4eb8-a5f7-c37cb13db341")
        private String assigneeId;
        @Schema(description = "任务结果", example = "竞品分析结果如下 ...")
        private String result;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies;
        }

        public TaskStatus getStatus() {
            return status;
        }

        public void setStatus(TaskStatus status) {
            this.status = status;
        }

        public String getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(String assigneeId) {
            this.assigneeId = assigneeId;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }
}
