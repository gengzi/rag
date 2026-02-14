package com.gengzi.agentteams.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamWorkspace {

    // 团队唯一标识
    private final String id;
    // 团队名称
    private final String name;
    // 团队总目标（由 Team Lead 定义）
    private final String objective;
    // 团队成员列表（key: teammateId）
    private final Map<String, TeammateAgent> teammates;
    // 共享任务列表（key: taskId）
    private final Map<String, TeamTask> tasks;
    // 团队邮箱（成员间通信）
    private final List<TeamMessage> mailbox;
    // 创建时间
    private final Instant createdAt;

    public TeamWorkspace(String name, String objective) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.objective = objective;
        this.teammates = new LinkedHashMap<>();
        this.tasks = new LinkedHashMap<>();
        this.mailbox = new ArrayList<>();
        this.createdAt = Instant.now();
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
}
