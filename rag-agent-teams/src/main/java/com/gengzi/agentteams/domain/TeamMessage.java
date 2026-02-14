package com.gengzi.agentteams.domain;

import java.time.Instant;

public record TeamMessage(
        // 发送人 teammateId
        String fromId,
        // 接收人 teammateId
        String toId,
        // 消息内容
        String content,
        // 发送时间
        Instant createdAt
) {
}
