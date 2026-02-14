package com.gengzi.agentteams.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class CreateTaskRequest {

    @NotBlank
    @Schema(description = "任务标题", example = "收集竞品信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank
    @Schema(description = "任务详细描述", example = "列出 5 个竞品并总结定位", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @ArraySchema(arraySchema = @Schema(description = "依赖任务ID列表（依赖完成后本任务才可执行）"), schema = @Schema(example = "9b7dd9b8-2fbe-4f6e-8a30-cc3652b02db2"))
    private List<String> dependencies = new ArrayList<>();

    @Schema(description = "可选：初始指派成员ID", example = "2ef75da3-2a92-4eb8-a5f7-c37cb13db341")
    private String assigneeId;

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

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
    }
}
