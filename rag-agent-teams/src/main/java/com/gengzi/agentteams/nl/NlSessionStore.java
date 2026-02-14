package com.gengzi.agentteams.nl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NlSessionStore {

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    public SessionContext getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            SessionContext context = new SessionContext();
            context.setSessionId(UUID.randomUUID().toString());
            sessions.put(context.getSessionId(), context);
            return context;
        }
        return sessions.computeIfAbsent(sessionId, key -> {
            SessionContext context = new SessionContext();
            context.setSessionId(key);
            return context;
        });
    }

    public static class SessionContext {
        private String sessionId;
        private String lastTeamId;
        private final List<String> history = new ArrayList<>();

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getLastTeamId() {
            return lastTeamId;
        }

        public void setLastTeamId(String lastTeamId) {
            this.lastTeamId = lastTeamId;
        }

        public List<String> getHistory() {
            return history;
        }
    }
}
