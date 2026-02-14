package com.gengzi.agentteams.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeammateAgent {

    // 成员唯一ID
    private final String id;
    // 成员名称
    private final String name;
    // 角色职责（如 Researcher / Analyst）
    private final String role;
    // 该成员默认使用的模型名
    private final String model;
    // 成员私有历史上下文（简化为字符串列表）
    private final List<String> history;
    // 邮箱消费游标：标记已读到 mailbox 的哪个位置
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
