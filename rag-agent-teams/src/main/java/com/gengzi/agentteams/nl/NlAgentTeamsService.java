package com.gengzi.agentteams.nl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.agentteams.api.CreateTeamRequest;
import com.gengzi.agentteams.api.TeamStateResponse;
import com.gengzi.agentteams.domain.TaskStatus;
import com.gengzi.agentteams.domain.TeamTask;
import com.gengzi.agentteams.domain.TeammateAgent;
import com.gengzi.agentteams.domain.TeamWorkspace;
import com.gengzi.agentteams.service.AgentTaskRunnerService;
import com.gengzi.agentteams.service.LlmRetryService;
import com.gengzi.agentteams.service.TeamRegistryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class NlAgentTeamsService {

    private final NlSessionStore sessionStore;
    private final TeamRegistryService teamRegistryService;
    private final AgentTaskRunnerService taskRunnerService;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final String configuredModel;
    private final LlmRetryService llmRetryService;

    public NlAgentTeamsService(
            NlSessionStore sessionStore,
            TeamRegistryService teamRegistryService,
            AgentTaskRunnerService taskRunnerService,
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.options.model:gemini-3-pro-low}") String configuredModel,
            LlmRetryService llmRetryService
    ) {
        this.sessionStore = sessionStore;
        this.teamRegistryService = teamRegistryService;
        this.taskRunnerService = taskRunnerService;
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
        this.configuredModel = configuredModel;
        this.llmRetryService = llmRetryService;
    }

    public NlChatResponse handle(NlChatRequest request, Consumer<NlWorkflowEvent> eventSink) {
        NlSessionStore.SessionContext session = sessionStore.getOrCreate(request.getSessionId());
        List<NlWorkflowEvent> collectedEvents = new ArrayList<>();
        Consumer<NlWorkflowEvent> sink = event -> {
            collectedEvents.add(event);
            eventSink.accept(event);
        };
        emit(sink, "RECEIVED", "收到自然语言请求", request.getMessage());

        String teamId = request.getTeamId();
        if ((teamId == null || teamId.isBlank()) && session.getLastTeamId() != null) {
            teamId = session.getLastTeamId();
        }

        NlIntent intent = parseIntent(request.getMessage(), teamId, session);
        emit(sink, "INTENT_PARSED", "意图解析完成", intent);

        String assistantReply = intent.getAssistantReply();
        for (NlIntent.Action action : intent.getActions()) {
            String type = safeUpper(action.getType());
            emit(sink, "ACTION_START", "开始执行动作 " + type, action);
            switch (type) {
                case "CREATE_TEAM" -> teamId = doCreateTeam(action, session);
                case "ADD_TASK" -> doAddTask(teamId, action, sink);
                case "SEND_MESSAGE" -> doSendMessage(teamId, action);
                case "BROADCAST" -> doBroadcastMessage(teamId, action);
                case "RUN_TASK" -> {
                    String pauseMessage = runTeamUntilFinished(teamId, sink);
                    if (pauseMessage != null && !pauseMessage.isBlank()) {
                        assistantReply = pauseMessage;
                    }
                }
                case "GET_STATE" -> teamRegistryService.getState(requireTeamId(teamId));
                case "CHAT" -> {
                    if (assistantReply == null || assistantReply.isBlank()) {
                        assistantReply = doChatReply(teamId, request.getMessage(), session);
                    }
                }
                default -> emit(sink, "ACTION_SKIP", "未知动作，已跳过: " + type, action);
            }
            emit(sink, "ACTION_END", "动作执行完成 " + type, null);
        }

        if ((intent.getActions() == null || intent.getActions().isEmpty())
                && (assistantReply == null || assistantReply.isBlank())) {
            assistantReply = doChatReply(teamId, request.getMessage(), session);
        }
        if (assistantReply == null || assistantReply.isBlank()) {
            assistantReply = "已完成执行。";
        }

        if (teamId != null && !teamId.isBlank()) {
            session.setLastTeamId(teamId);
        }
        appendHistory(session, "user", request.getMessage());
        appendHistory(session, "assistant", assistantReply);

        NlChatResponse response = new NlChatResponse();
        response.setSessionId(session.getSessionId());
        response.setTeamId(teamId);
        response.setAssistantReply(assistantReply);
        response.setEvents(collectedEvents);
        if (teamId != null && !teamId.isBlank()) {
            response.setState(teamRegistryService.getState(teamId));
        }
        return response;
    }

    private String doCreateTeam(NlIntent.Action action, NlSessionStore.SessionContext session) {
        List<CreateTeamRequest.TeammateSpec> teammates = new ArrayList<>();
        for (NlIntent.TeammateSpec spec : action.getTeammates()) {
            CreateTeamRequest.TeammateSpec teammate = new CreateTeamRequest.TeammateSpec();
            teammate.setName(spec.getName());
            teammate.setRole(spec.getRole() == null ? "Generalist" : spec.getRole());
            teammate.setModel(configuredModel);
            teammates.add(teammate);
        }
        TeamWorkspace team = teamRegistryService.createTeam(
                valueOr(action.getTeamName(), "NL Team"),
                valueOr(action.getObjective(), "通过自然语言持续编排任务并交付结果"),
                teammates
        );
        session.setLastTeamId(team.getId());
        return team.getId();
    }

    private void doAddTask(String teamId, NlIntent.Action action, Consumer<NlWorkflowEvent> sink) {
        String resolvedTeamId = requireTeamId(teamId);
        TeamWorkspace team = teamRegistryService.getTeam(resolvedTeamId);
        String assigneeId = resolveTeammateId(team, action.getAssigneeId(), action.getAssigneeName());
        if (assigneeId == null || assigneeId.isBlank()) {
            assigneeId = selectExecutorForPlannedTask(team);
        }
        TeamTask created = teamRegistryService.createTask(
                resolvedTeamId,
                valueOr(action.getTitle(), "未命名任务"),
                valueOr(action.getDescription(), "由自然语言创建的任务"),
                action.getDependencyTaskIds() == null ? List.of() : action.getDependencyTaskIds(),
                assigneeId
        );
        emit(sink, "TASK_ASSIGNED", "任务已规划并指定执行人",
                Map.of("taskId", created.getId(), "assigneeId", assigneeId));
    }

    private void doSendMessage(String teamId, NlIntent.Action action) {
        String resolvedTeamId = requireTeamId(teamId);
        TeamWorkspace team = teamRegistryService.getTeam(resolvedTeamId);
        String fromId = resolveTeammateId(team, action.getFromId(), action.getFromName());
        String toId = resolveTeammateId(team, action.getToId(), action.getToName());
        teamRegistryService.sendMessage(
                resolvedTeamId,
                fromId,
                toId,
                valueOr(action.getContent(), "请继续推进当前任务")
        );
    }

    private void doBroadcastMessage(String teamId, NlIntent.Action action) {
        String resolvedTeamId = requireTeamId(teamId);
        TeamWorkspace team = teamRegistryService.getTeam(resolvedTeamId);
        String fromId = resolveTeammateId(team, action.getFromId(), action.getFromName());
        if (fromId == null || fromId.isBlank()) {
            throw new IllegalArgumentException("BROADCAST 需要 fromId 或 fromName");
        }
        String content = valueOr(action.getContent(), "团队同步：请更新当前进展。");
        for (TeammateAgent teammate : team.getTeammates().values()) {
            if (!teammate.getId().equals(fromId)) {
                teamRegistryService.sendMessage(resolvedTeamId, fromId, teammate.getId(), content);
            }
        }
    }

    private String runTeamUntilFinished(String teamId, Consumer<NlWorkflowEvent> sink) {
        String resolvedTeamId = requireTeamId(teamId);
        emit(sink, "RUN_LOOP_START", "开始自动执行任务流程", Map.of("teamId", resolvedTeamId));

        int round = 0;
        int maxRounds = 200;
        while (round < maxRounds) {
            round++;
            TeamWorkspace team = teamRegistryService.getTeam(resolvedTeamId);

            LeaderDecision decision = leaderReviewAndAdjust(team, sink);
            if (decision.needsUserInput) {
                String question = valueOr(decision.userQuestion, "需要你确认下一步策略，请回复后继续。");
                emit(sink, "WAIT_USER_INPUT", "等待用户指令", Map.of("question", question));
                return question;
            }

            List<TeamTask> allTasks = team.getTasks().values().stream()
                    .sorted(Comparator.comparing(TeamTask::getCreatedAt))
                    .toList();
            if (allTasks.isEmpty()) {
                emit(sink, "RUN_LOOP_END", "当前没有任务可执行", null);
                return null;
            }

            boolean allDone = allTasks.stream().allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
            if (allDone) {
                emit(sink, "RUN_LOOP_END", "所有任务已完成", Map.of("round", round));
                return null;
            }

            List<TeamTask> inProgress = allTasks.stream()
                    .filter(task -> task.getStatus() == TaskStatus.IN_PROGRESS)
                    .toList();
            List<TeamTask> readyPending = allTasks.stream()
                    .filter(task -> task.getStatus() == TaskStatus.PENDING)
                    .filter(task -> teamRegistryService.isTaskReady(team, task))
                    .toList();

            if (inProgress.isEmpty() && readyPending.isEmpty()) {
                emit(sink, "RUN_BLOCKED", "任务执行被阻塞：存在未满足依赖或循环依赖", null);
                return null;
            }

            emit(sink, "ROUND_START", "开始执行轮次 " + round,
                    Map.of("inProgress", inProgress.size(), "ready", readyPending.size()));

            for (TeamTask task : inProgress) {
                executeSingleTask(resolvedTeamId, team, task, task.getAssigneeId(), sink);
            }
            for (TeamTask task : readyPending) {
                String assigneeId = task.getAssigneeId();
                if (assigneeId == null || assigneeId.isBlank()) {
                    assigneeId = selectExecutorForPlannedTask(team);
                }
                executeSingleTask(resolvedTeamId, team, task, assigneeId, sink);
            }

            emit(sink, "ROUND_END", "完成执行轮次 " + round, null);
        }

        emit(sink, "RUN_ABORT", "达到最大执行轮次，已停止", Map.of("maxRounds", maxRounds));
        return null;
    }

    private LeaderDecision leaderReviewAndAdjust(TeamWorkspace team, Consumer<NlWorkflowEvent> sink) {
        String leaderId = findLeaderId(team);
        if (leaderId == null) {
            return new LeaderDecision();
        }

        String stateJson = writeJsonSafe(teamRegistryService.getState(team.getId()));
        String prompt = """
                你是团队 leader，基于当前执行状态做一次协调决策。
                返回 JSON：
                {
                  "needsUserInput": false,
                  "userQuestion": "",
                  "actions": [
                    {
                      "type": "ADD_TASK|SEND_MESSAGE|BROADCAST|NONE",
                      "title": "",
                      "description": "",
                      "assigneeId": "",
                      "assigneeName": "",
                      "content": "",
                      "toId": "",
                      "toName": ""
                    }
                  ]
                }
                规则：
                1) 仅返回 JSON。
                2) 若需要用户确认方向/约束，needsUserInput=true 并给 userQuestion。
                3) 能继续自动执行时优先给 actions，不要频繁中断。
                4) 不要创建重复任务。
                当前状态:
                %s
                """.formatted(stateJson);

        String raw = llmRetryService.executeWithRetry(() -> chatClientBuilder.build().prompt().user(prompt).call().content());
        String json = stripCodeFence(raw);
        LeaderDecision decision;
        try {
            decision = objectMapper.readValue(json, LeaderDecision.class);
        } catch (JsonProcessingException e) {
            decision = new LeaderDecision();
            decision.needsUserInput = false;
            decision.actions = List.of();
        }
        if (decision.actions == null) {
            decision.actions = List.of();
        }

        for (LeaderAction action : decision.actions) {
            String type = safeUpper(action.type);
            switch (type) {
                case "ADD_TASK" -> {
                    String assigneeId = resolveTeammateId(team, action.assigneeId, action.assigneeName);
                    if (assigneeId == null || assigneeId.isBlank()) {
                        assigneeId = selectExecutorForPlannedTask(team);
                    }
                    TeamTask task = teamRegistryService.createTask(
                            team.getId(),
                            valueOr(action.title, "leader 补充任务"),
                            valueOr(action.description, "leader 根据执行进度动态补充"),
                            List.of(),
                            assigneeId
                    );
                    emit(sink, "LEADER_ADDED_TASK", "leader 动态新增任务",
                            Map.of("taskId", task.getId(), "assigneeId", assigneeId));
                }
                case "SEND_MESSAGE" -> {
                    String toId = resolveTeammateId(team, action.toId, action.toName);
                    if (toId != null && !toId.isBlank()) {
                        teamRegistryService.sendMessage(
                                team.getId(),
                                leaderId,
                                toId,
                                valueOr(action.content, "请按最新计划推进任务")
                        );
                        emit(sink, "LEADER_MESSAGE", "leader 发送定向协作消息",
                                Map.of("to", toId));
                    }
                }
                case "BROADCAST" -> {
                    for (TeammateAgent teammate : team.getTeammates().values()) {
                        if (!teammate.getId().equals(leaderId)) {
                            teamRegistryService.sendMessage(
                                    team.getId(),
                                    leaderId,
                                    teammate.getId(),
                                    valueOr(action.content, "团队同步：请按最新计划执行")
                            );
                        }
                    }
                    emit(sink, "LEADER_BROADCAST", "leader 广播协作消息", null);
                }
                default -> {
                    // NONE 或未知动作忽略
                }
            }
        }
        return decision;
    }

    private void executeSingleTask(
            String teamId,
            TeamWorkspace team,
            TeamTask task,
            String teammateId,
            Consumer<NlWorkflowEvent> sink
    ) {
        if (teammateId == null || teammateId.isBlank()) {
            teammateId = selectExecutorForPlannedTask(team);
        }
        String leaderId = findLeaderId(team);

        if (leaderId != null && !leaderId.equals(teammateId)) {
            teamRegistryService.sendMessage(
                    teamId,
                    leaderId,
                    teammateId,
                    "请执行任务：" + task.getTitle() + "。完成后同步结果。"
            );
            emit(sink, "MESSAGE_SENT", "leader 已下发任务消息",
                    Map.of("taskId", task.getId(), "from", leaderId, "to", teammateId));
        }

        emit(sink, "TASK_RUN_START", "开始执行任务 " + task.getId(),
                Map.of("taskId", task.getId(), "assigneeId", teammateId));
        String output = taskRunnerService.runTask(teamId, task.getId(), teammateId);
        emit(sink, "TASK_RUN_END", "任务执行完成", Map.of("taskId", task.getId()));

        String summary = summarize(output, 220);
        if (leaderId != null && !leaderId.equals(teammateId)) {
            teamRegistryService.sendMessage(
                    teamId,
                    teammateId,
                    leaderId,
                    "任务完成：" + task.getTitle() + "。摘要：" + summary
            );
        }
        for (TeammateAgent mate : team.getTeammates().values()) {
            if (!mate.getId().equals(teammateId) && !mate.getId().equals(leaderId)) {
                teamRegistryService.sendMessage(
                        teamId,
                        teammateId,
                        mate.getId(),
                        "同步任务结果：" + task.getTitle() + "。摘要：" + summary
                );
            }
        }
        emit(sink, "MESSAGE_SYNCED", "任务结果已同步给团队", Map.of("taskId", task.getId()));
    }

    private String doChatReply(String teamId, String userMessage, NlSessionStore.SessionContext session) {
        String stateJson = "{}";
        if (teamId != null && !teamId.isBlank()) {
            TeamStateResponse state = teamRegistryService.getState(teamId);
            stateJson = writeJsonSafe(state);
        }
        final String stateJsonFinal = stateJson;
        final String history = String.join("\n", session.getHistory());
        ChatClient chatClient = chatClientBuilder.build();
        return llmRetryService.executeWithRetry(() -> chatClient.prompt()
                .system("""
                        你是 Agent Teams 的中文助手。请结合团队状态回答用户问题。
                        - 如果用户在对话中提出下一步建议，请给出可执行建议。
                        - 回答简洁直接，优先说明当前进度、阻塞点、下一步。
                        """)
                .user("""
                        团队状态JSON:
                        %s

                        会话历史:
                        %s

                        用户消息:
                        %s
                        """.formatted(stateJsonFinal, history, userMessage))
                .call()
                .content());
    }

    private NlIntent parseIntent(String userMessage, String teamId, NlSessionStore.SessionContext session) {
        String prompt = """
                你是一个 Agent Teams 指令解析器。把用户自然语言转换为 JSON，字段如下：
                {
                  "assistantReply": "可选，给用户的文字答复",
                  "actions": [
                    {
                      "type": "CREATE_TEAM|ADD_TASK|SEND_MESSAGE|BROADCAST|RUN_TASK|GET_STATE|CHAT",
                      "teamName": "",
                      "objective": "",
                      "teammates": [{"name":"","role":"","model":""}],
                      "title": "",
                      "description": "",
                      "dependencyTaskIds": [],
                      "assigneeId": "",
                      "assigneeName": "",
                      "taskId": "",
                      "taskTitle": "",
                      "fromId": "",
                      "fromName": "",
                      "toId": "",
                      "toName": "",
                      "content": "",
                      "teammateId": "",
                      "teammateName": ""
                    }
                  ]
                }
                规则：
                1) 只返回 JSON，不要 markdown。
                2) RUN_TASK 表示开始自动持续执行，直到全部任务结束或进入等待用户。
                3) ADD_TASK 尽量指定 assigneeId/assigneeName。
                当前团队ID：%s
                会话历史：
                %s
                用户输入：
                %s
                """.formatted(teamId == null ? "" : teamId, String.join("\n", session.getHistory()), userMessage);
        String raw = llmRetryService.executeWithRetry(() -> chatClientBuilder.build().prompt().user(prompt).call().content());
        String json = stripCodeFence(raw);
        try {
            NlIntent intent = objectMapper.readValue(json, NlIntent.class);
            if (intent.getActions() == null) {
                intent.setActions(new ArrayList<>());
            }
            return intent;
        } catch (JsonProcessingException ex) {
            NlIntent fallback = new NlIntent();
            NlIntent.Action chat = new NlIntent.Action();
            chat.setType("CHAT");
            fallback.setActions(List.of(chat));
            fallback.setAssistantReply("我已收到请求，但结构化解析失败，已切换为对话模式继续响应。");
            return fallback;
        }
    }

    private String requireTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("当前会话没有可用 teamId，请先创建团队或显式传入 teamId。");
        }
        return teamId;
    }

    private String resolveTeammateId(TeamWorkspace team, String id, String name) {
        if (id != null && !id.isBlank()) {
            return id;
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<String> found = team.getTeammates().values().stream()
                .filter(teammate -> name.equalsIgnoreCase(teammate.getName()))
                .map(TeammateAgent::getId)
                .findFirst();
        return found.orElse(null);
    }

    private String selectExecutorForPlannedTask(TeamWorkspace team) {
        List<TeammateAgent> all = team.getTeammates().values().stream().toList();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("团队没有可用 agent");
        }
        List<TeammateAgent> executors = all.stream()
                .filter(teammate -> !isLeaderRole(teammate.getRole()))
                .toList();
        if (executors.isEmpty()) {
            executors = all;
        }
        Map<String, Long> inProgressByAssignee = team.getTasks().values().stream()
                .filter(task -> task.getStatus() == TaskStatus.IN_PROGRESS)
                .filter(task -> task.getAssigneeId() != null && !task.getAssigneeId().isBlank())
                .collect(Collectors.groupingBy(TeamTask::getAssigneeId, Collectors.counting()));
        return executors.stream()
                .min(Comparator
                        .comparingLong((TeammateAgent t) -> inProgressByAssignee.getOrDefault(t.getId(), 0L))
                        .thenComparing(TeammateAgent::getId))
                .map(TeammateAgent::getId)
                .orElseThrow(() -> new IllegalArgumentException("没有可分配的执行 agent"));
    }

    private String findLeaderId(TeamWorkspace team) {
        return team.getTeammates().values().stream()
                .filter(teammate -> isLeaderRole(teammate.getRole()))
                .map(TeammateAgent::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean isLeaderRole(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.toLowerCase(Locale.ROOT);
        return lower.contains("leader")
                || lower.contains("lead")
                || lower.contains("manager")
                || lower.contains("planner")
                || lower.contains("orchestrator");
    }

    private String summarize(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    private void appendHistory(NlSessionStore.SessionContext session, String role, String content) {
        session.getHistory().add(role + ": " + content);
        if (session.getHistory().size() > 30) {
            session.getHistory().remove(0);
        }
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private String valueOr(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void emit(Consumer<NlWorkflowEvent> sink, String type, String message, Object data) {
        sink.accept(NlWorkflowEvent.of(type, message, data));
    }

    private String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private String writeJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static class LeaderDecision {
        public boolean needsUserInput;
        public String userQuestion;
        public List<LeaderAction> actions = List.of();
    }

    private static class LeaderAction {
        public String type;
        public String title;
        public String description;
        public String assigneeId;
        public String assigneeName;
        public String content;
        public String toId;
        public String toName;
    }
}
