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
