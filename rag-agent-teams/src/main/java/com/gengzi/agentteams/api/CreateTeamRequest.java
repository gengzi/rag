package com.gengzi.agentteams.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class CreateTeamRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String objective;

    @Valid
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
        private String name;

        @NotBlank
        private String role;

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
