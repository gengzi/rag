package com.gengzi.rag.agent.myagent.memory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 记忆类 - 管理代理的消息历史
 * 使用Spring AI的Message类型，以便与ChatClient无缝集成
 */
@Slf4j
@Data
public class Memory {

    private List<Message> messages = new ArrayList<>();

    /**
     * 添加消息
     */
    public void addMessage(Message message) {
        if (message != null) {
            messages.add(message);
            log.debug("添加消息: {} - {}", message.getMessageType(), message.getText());
        }
    }

    /**
     * 添加多条消息
     */
    public void addMessages(List<Message> newMessages) {
        if (newMessages != null) {
            for (Message message : newMessages) {
                addMessage(message);
            }
        }
    }

    /**
     * 获取最后一条消息
     */
    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * 清空记忆
     */
    public void clear() {
        messages.clear();
        log.debug("记忆已清空");
    }

    /**
     * 清空工具执行历史
     * 移除所有工具相关的消息和ReAct过程中的思考消息
     */
    public void clearToolContext() {
        Iterator<Message> iterator = messages.iterator();
        int removedCount = 0;

        while (iterator.hasNext()) {
            Message message = iterator.next();

            // 移除工具消息
            if (message instanceof ToolResponseMessage) {
                iterator.remove();
                removedCount++;
                continue;
            }

            // 移除包含工具调用的助手消息
            if (message instanceof AssistantMessage assistantMessage) {
                if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                    iterator.remove();
                    removedCount++;
                    continue;
                }
            }

            // 移除ReAct相关的思考和观察消息
            if (isReActMessage(message)) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.debug("清空工具上下文，移除了 {} 条消息", removedCount);
        }
    }

    /**
     * 判断是否为ReAct相关消息（思考、行动、观察）
     */
    private boolean isReActMessage(Message message) {
        if (message.getText() == null) {
            return false;
        }

        String content = message.getText();
        return content.startsWith("思考:") ||
               content.startsWith("行动:") ||
               content.startsWith("观察:") ||
               content.startsWith("根据当前状态和可用工具，确定下一步行动");
    }

    /**
     * 格式化消息为字符串（用于日志或调试）
     */
    public String getFormatMessage() {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            String messageType = getMessageTypeString(message);
            String content = message.getText();
            sb.append(String.format("%s: %s\n", messageType, content));
        }
        return sb.toString();
    }

    /**
     * 获取消息类型字符串表示
     */
    private String getMessageTypeString(Message message) {
        if (message instanceof UserMessage) {
            return "用户";
        } else if (message instanceof AssistantMessage) {
            return "助手";
        } else if (message instanceof SystemMessage) {
            return "系统";
        } else if (message instanceof ToolResponseMessage) {
            return "工具";
        } else {
            return message.getMessageType().toString();
        }
    }

    /**
     * 获取消息数量
     */
    public int size() {
        return messages.size();
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * 获取指定索引的消息
     */
    public Message get(int index) {
        return messages.get(index);
    }

    // ==================== 便捷方法 ====================

    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        addMessage(new UserMessage(content));
    }



    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        addMessage(new AssistantMessage(content));
    }

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        addMessage(new SystemMessage(content));
    }



    public void addToolMessage(List<ToolResponseMessage.ToolResponse> responses) {
        addMessage(new ToolResponseMessage(responses));
    }


    // ==================== 消息过滤和查询方法 ====================

    /**
     * 获取所有用户消息
     */
    public List<Message> getUserMessages() {
        return messages.stream()
                .filter(message -> message instanceof UserMessage)
                .toList();
    }

    /**
     * 获取所有助手消息
     */
    public List<Message> getAssistantMessages() {
        return messages.stream()
                .filter(message -> message instanceof AssistantMessage)
                .toList();
    }

    /**
     * 获取所有系统消息
     */
    public List<Message> getSystemMessages() {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .toList();
    }

    /**
     * 获取所有工具消息
     */
    public List<Message> getToolMessages() {
        return messages.stream()
                .filter(message -> message instanceof ToolResponseMessage)
                .toList();
    }

    /**
     * 获取最近的N条消息（不包括系统消息）
     */
    public List<Message> getRecentMessages(int count) {
        return messages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .skip(Math.max(0, messages.size() - count))
                .toList();
    }

    /**
     * 获取用于ChatClient的消息列表（过滤掉系统消息，因为系统消息通常通过prompt()设置）
     */
    public List<Message> getMessagesForChatClient() {
        return messages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .toList();
    }

    /**
     * 获取完整的消息列表（包括系统消息）
     */
    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    /**
     * 克隆记忆（创建副本）
     */
    public Memory clone() {
        Memory clonedMemory = new Memory();
        clonedMemory.addMessages(this.messages);
        return clonedMemory;
    }

    /**
     * 估算记忆使用大小（字符数）
     */
    public int getEstimatedSize() {
        return messages.stream()
                .mapToInt(message -> message.getText() != null ? message.getText().length() : 0)
                .sum();
    }

    @Override
    public String toString() {
        return String.format("Memory{messageCount=%d, estimatedSize=%d chars}",
                messages.size(), getEstimatedSize());
    }
}