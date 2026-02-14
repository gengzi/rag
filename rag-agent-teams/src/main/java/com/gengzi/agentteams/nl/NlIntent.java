package com.gengzi.agentteams.nl;

import java.util.ArrayList;
import java.util.List;

public class NlIntent {

    private List<Action> actions = new ArrayList<>();
    private String assistantReply;

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public String getAssistantReply() {
        return assistantReply;
    }

    public void setAssistantReply(String assistantReply) {
        this.assistantReply = assistantReply;
    }

    public static class Action {
        private String type;
        private String teamName;
        private String objective;
        private List<TeammateSpec> teammates = new ArrayList<>();
        private String title;
        private String description;
        private List<String> dependencyTaskIds = new ArrayList<>();
        private String assigneeId;
        private String assigneeName;
        private String taskId;
        private String taskTitle;
        private String fromId;
        private String fromName;
        private String toId;
        private String toName;
        private String content;
        private String teammateId;
        private String teammateName;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTeamName() {
            return teamName;
        }

        public void setTeamName(String teamName) {
            this.teamName = teamName;
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

        public List<String> getDependencyTaskIds() {
            return dependencyTaskIds;
        }

        public void setDependencyTaskIds(List<String> dependencyTaskIds) {
            this.dependencyTaskIds = dependencyTaskIds;
        }

        public String getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(String assigneeId) {
            this.assigneeId = assigneeId;
        }

        public String getAssigneeName() {
            return assigneeName;
        }

        public void setAssigneeName(String assigneeName) {
            this.assigneeName = assigneeName;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getTaskTitle() {
            return taskTitle;
        }

        public void setTaskTitle(String taskTitle) {
            this.taskTitle = taskTitle;
        }

        public String getFromId() {
            return fromId;
        }

        public void setFromId(String fromId) {
            this.fromId = fromId;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }

        public String getToId() {
            return toId;
        }

        public void setToId(String toId) {
            this.toId = toId;
        }

        public String getToName() {
            return toName;
        }

        public void setToName(String toName) {
            this.toName = toName;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getTeammateId() {
            return teammateId;
        }

        public void setTeammateId(String teammateId) {
            this.teammateId = teammateId;
        }

        public String getTeammateName() {
            return teammateName;
        }

        public void setTeammateName(String teammateName) {
            this.teammateName = teammateName;
        }
    }

    public static class TeammateSpec {
        private String name;
        private String role;
        private String model;

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
