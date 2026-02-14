package com.gengzi.agentteams.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeammateAgent {

    private final String id;
    private final String name;
    private final String role;
    private final String model;
    private final List<String> history;
    private int mailboxCursor;

    public TeammateAgent(String name, String role, String model) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.role = role;
        this.model = model;
        this.history = new ArrayList<>();
        this.mailboxCursor = 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getModel() {
        return model;
    }

    public List<String> getHistory() {
        return history;
    }

    public int getMailboxCursor() {
        return mailboxCursor;
    }

    public void setMailboxCursor(int mailboxCursor) {
        this.mailboxCursor = mailboxCursor;
    }
}
