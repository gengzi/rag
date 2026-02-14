package com.gengzi.agentteams.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamWorkspace {

    private final String id;
    private final String name;
    private final String objective;
    private final Map<String, TeammateAgent> teammates;
    private final Map<String, TeamTask> tasks;
    private final List<TeamMessage> mailbox;
    private final Instant createdAt;
    // 计划版本号：每次计划变更（新增/修改/删除任务）自增
    private long planVersion;

    public TeamWorkspace(String name, String objective) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.objective = objective;
        this.teammates = new LinkedHashMap<>();
        this.tasks = new LinkedHashMap<>();
        this.mailbox = new ArrayList<>();
        this.createdAt = Instant.now();
        this.planVersion = 0L;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getObjective() {
        return objective;
    }

    public Map<String, TeammateAgent> getTeammates() {
        return teammates;
    }

    public Map<String, TeamTask> getTasks() {
        return tasks;
    }

    public List<TeamMessage> getMailbox() {
        return mailbox;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getPlanVersion() {
        return planVersion;
    }

    public long bumpPlanVersion() {
        this.planVersion++;
        return this.planVersion;
    }
}
