package com.gengzi.agentteams.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class CreateTeamRequest {

    @NotBlank
    @Schema(description = "团队名称", example = "Market Analysis Team", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Schema(description = "团队目标（Team Lead 的总体任务）", example = "调研北美 AI Agent 产品机会并输出结论", requiredMode = Schema.RequiredMode.REQUIRED)
    private String objective;

    @Valid
    @ArraySchema(schema = @Schema(implementation = TeammateSpec.class), arraySchema = @Schema(description = "团队成员列表"))
    private List<TeammateSpec> teammates = new ArrayList<>();

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

    public List<TeammateSpec> getTeammates() {
        return teammates;
    }

    public void setTeammates(List<TeammateSpec> teammates) {
        this.teammates = teammates;
    }

    public static class TeammateSpec {
        @NotBlank
        @Schema(description = "成员名称", example = "Alice", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @NotBlank
        @Schema(description = "成员职责角色", example = "Researcher", requiredMode = Schema.RequiredMode.REQUIRED)
        private String role;

        @Schema(description = "成员默认模型", example = "gpt-4o-mini")
        private String model = "gpt-4o-mini";

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
}
