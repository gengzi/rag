package com.gengzi.agentteams.domain;

import java.time.Instant;

public record TeamMessage(
        String fromId,
        String toId,
        String content,
        Instant createdAt
) {
}
