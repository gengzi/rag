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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NlAgentTeamsService.class);

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
        String teamId = (request.getTeamId() == null || request.getTeamId().isBlank()) ? session.getLastTeamId() : request.getTeamId();

        List<NlWorkflowEvent> events = new ArrayList<>();
        Consumer<NlWorkflowEvent> sink = e -> {
            events.add(e);
            eventSink.accept(e);
        };
        emit(sink, "RECEIVED", "收到自然语言请求", request.getMessage());

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
                case "BROADCAST" -> doBroadcast(teamId, action);
                case "RUN_TASK" -> {
                    String pauseMessage = runUntilFinished(teamId, sink);
                    if (pauseMessage != null && !pauseMessage.isBlank()) {
                        assistantReply = pauseMessage;
                    }
                }
                case "CHAT" -> assistantReply = doChatReply(teamId, request.getMessage(), session);
                case "GET_STATE" -> teamRegistryService.getState(requireTeamId(teamId));
                default -> emit(sink, "ACTION_SKIP", "未知动作，已跳过: " + type, null);
            }
            emit(sink, "ACTION_END", "动作执行完成 " + type, null);
        }

        if (assistantReply == null || assistantReply.isBlank()) {
            assistantReply = "已完成执行。";
        }
        if (teamId != null && !teamId.isBlank()) {
            session.setLastTeamId(teamId);
        }
        session.getHistory().add("user: " + request.getMessage());
        session.getHistory().add("assistant: " + assistantReply);
        if (session.getHistory().size() > 30) {
            session.getHistory().subList(0, session.getHistory().size() - 30).clear();
        }

        NlChatResponse resp = new NlChatResponse();
        resp.setSessionId(session.getSessionId());
        resp.setTeamId(teamId);
        resp.setAssistantReply(assistantReply);
        resp.setEvents(events);
        if (teamId != null && !teamId.isBlank()) {
            resp.setState(teamRegistryService.getState(teamId));
        }
        return resp;
    }

    private String doCreateTeam(NlIntent.Action action, NlSessionStore.SessionContext session) {
        List<CreateTeamRequest.TeammateSpec> specs = new ArrayList<>();
        for (NlIntent.TeammateSpec s : action.getTeammates()) {
            CreateTeamRequest.TeammateSpec t = new CreateTeamRequest.TeammateSpec();
            t.setName(s.getName());
            t.setRole(s.getRole() == null ? "Generalist" : s.getRole());
            t.setModel(configuredModel);
            specs.add(t);
        }
        TeamWorkspace team = teamRegistryService.createTeam(
                valueOr(action.getTeamName(), "NL Team"),
                valueOr(action.getObjective(), "通过自然语言持续编排任务并交付结果"),
                specs
        );
        session.setLastTeamId(team.getId());
        return team.getId();
    }

    private void doAddTask(String teamId, NlIntent.Action action, Consumer<NlWorkflowEvent> sink) {
        TeamWorkspace team = teamRegistryService.getTeam(requireTeamId(teamId));
        String assigneeId = resolveTeammateId(team, action.getAssigneeId(), action.getAssigneeName());
        if (assigneeId == null || assigneeId.isBlank()) {
            assigneeId = selectExecutor(team);
        }
        TeamTask task = teamRegistryService.createTask(team.getId(),
                valueOr(action.getTitle(), "未命名任务"),
                valueOr(action.getDescription(), "由自然语言创建的任务"),
                action.getDependencyTaskIds() == null ? List.of() : action.getDependencyTaskIds(),
                assigneeId);
        emit(sink, "TASK_ASSIGNED", "任务已规划并指定执行人", Map.of("taskId", task.getId(), "assigneeId", assigneeId));
    }

    private void doSendMessage(String teamId, NlIntent.Action action) {
        TeamWorkspace team = teamRegistryService.getTeam(requireTeamId(teamId));
        String fromId = resolveTeammateId(team, action.getFromId(), action.getFromName());
        String toId = resolveTeammateId(team, action.getToId(), action.getToName());
        teamRegistryService.sendMessage(team.getId(), fromId, toId, valueOr(action.getContent(), "请继续推进当前任务"));
    }

    private void doBroadcast(String teamId, NlIntent.Action action) {
        TeamWorkspace team = teamRegistryService.getTeam(requireTeamId(teamId));
        String fromId = resolveTeammateId(team, action.getFromId(), action.getFromName());
        for (TeammateAgent t : team.getTeammates().values()) {
            if (!t.getId().equals(fromId)) {
                teamRegistryService.sendMessage(team.getId(), fromId, t.getId(), valueOr(action.getContent(), "团队同步：请更新进展"));
            }
        }
    }

    private String runUntilFinished(String teamId, Consumer<NlWorkflowEvent> sink) {
        TeamWorkspace team = teamRegistryService.getTeam(requireTeamId(teamId));
        for (int round = 1; round <= 200; round++) {
            LeaderDecision d = leaderReviewAndAdjust(team, sink);
            if (d.needsUserInput) {
                String q = valueOr(d.userQuestion, "需要你确认下一步，请回复后继续。");
                emit(sink, "WAIT_USER_INPUT", "等待用户指令", Map.of("question", q));
                return q;
            }
            List<TeamTask> all = team.getTasks().values().stream().sorted(Comparator.comparing(TeamTask::getCreatedAt)).toList();
            if (all.isEmpty() || all.stream().allMatch(t -> t.getStatus() == TaskStatus.COMPLETED)) {
                emit(sink, "RUN_LOOP_END", "所有任务已完成", Map.of("round", round));
                return null;
            }
            List<TeamTask> ready = all.stream()
                    .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS || (t.getStatus() == TaskStatus.PENDING && teamRegistryService.isTaskReady(team, t)))
                    .toList();
            if (ready.isEmpty()) {
                emit(sink, "RUN_BLOCKED", "任务执行被阻塞", null);
                return null;
            }
            emit(sink, "ROUND_START", "开始执行轮次 " + round, Map.of("ready", ready.size()));
            for (TeamTask task : ready) {
                String assignee = (task.getAssigneeId() == null || task.getAssigneeId().isBlank()) ? selectExecutor(team) : task.getAssigneeId();
                emit(sink, "TASK_RUN_START", "开始执行任务 " + task.getId(), Map.of("taskId", task.getId(), "assigneeId", assignee));
                taskRunnerService.runTask(team.getId(), task.getId(), assignee);
                emit(sink, "TASK_RUN_END", "任务执行完成", Map.of("taskId", task.getId()));
            }
            emit(sink, "ROUND_END", "完成执行轮次 " + round, null);
        }
        emit(sink, "RUN_ABORT", "达到最大轮次，已停止", null);
        return null;
    }

    private LeaderDecision leaderReviewAndAdjust(TeamWorkspace team, Consumer<NlWorkflowEvent> sink) {
        String leaderId = findLeaderId(team);
        if (leaderId == null) {
            return new LeaderDecision();
        }
        String prompt = """
                你是团队leader。基于状态做决策，仅返回JSON:
                {"needsUserInput":false,"userQuestion":"","actions":[{"type":"ADD_TASK|UPDATE_TASK|DELETE_TASK|SEND_MESSAGE|BROADCAST|NONE","taskId":"","title":"","description":"","dependencyTaskIds":[],"assigneeId":"","assigneeName":"","content":"","toId":"","toName":""}]}
                当前状态:%s
                """.formatted(writeJsonSafe(teamRegistryService.getState(team.getId())));
        String raw = llmRetryService.executeWithRetry(() -> chatClientBuilder.build().prompt().user(prompt).call().content());
        LeaderDecision d;
        try {
            d = objectMapper.readValue(stripCodeFence(raw), LeaderDecision.class);
        } catch (Exception e) {
            d = new LeaderDecision();
        }
        if (d.actions == null) {
            d.actions = List.of();
        }
        for (LeaderAction a : d.actions) {
            String type = safeUpper(a.type);
            switch (type) {
                case "ADD_TASK" -> doLeaderAdd(team, a, sink);
                case "UPDATE_TASK" -> doLeaderUpdate(team, a, sink);
                case "DELETE_TASK" -> doLeaderDelete(team, a, sink);
                case "SEND_MESSAGE" -> {
                    String toId = resolveTeammateId(team, a.toId, a.toName);
                    if (toId != null && !toId.isBlank()) {
                        teamRegistryService.sendMessage(team.getId(), leaderId, toId, valueOr(a.content, "请按计划推进"));
                    }
                }
                case "BROADCAST" -> {
                    for (TeammateAgent t : team.getTeammates().values()) {
                        if (!t.getId().equals(leaderId)) {
                            teamRegistryService.sendMessage(team.getId(), leaderId, t.getId(), valueOr(a.content, "团队同步"));
                        }
                    }
                }
                default -> {
                }
            }
        }
        return d;
    }

    private void doLeaderAdd(TeamWorkspace team, LeaderAction a, Consumer<NlWorkflowEvent> sink) {
        String assignee = resolveTeammateId(team, a.assigneeId, a.assigneeName);
        if (assignee == null || assignee.isBlank()) {
            assignee = selectExecutor(team);
        }
        TeamTask t = teamRegistryService.createTask(team.getId(), valueOr(a.title, "leader补充任务"), valueOr(a.description, "leader动态补充"), List.of(), assignee);
        emit(sink, "LEADER_ADDED_TASK", "leader新增任务", Map.of("taskId", t.getId()));
    }

    private void doLeaderUpdate(TeamWorkspace team, LeaderAction a, Consumer<NlWorkflowEvent> sink) {
        if (a.taskId == null || a.taskId.isBlank()) {
            return;
        }
        String assignee = resolveTeammateId(team, a.assigneeId, a.assigneeName);
        TeamTask t = teamRegistryService.updateTaskPlan(team.getId(), a.taskId, a.title, a.description, a.dependencyTaskIds, assignee);
        emit(sink, "LEADER_UPDATED_TASK", "leader更新任务", Map.of("taskId", t.getId()));
    }

    private void doLeaderDelete(TeamWorkspace team, LeaderAction a, Consumer<NlWorkflowEvent> sink) {
        if (a.taskId == null || a.taskId.isBlank()) {
            return;
        }
        teamRegistryService.deleteTaskPlan(team.getId(), a.taskId);
        emit(sink, "LEADER_DELETED_TASK", "leader删除任务", Map.of("taskId", a.taskId));
    }

    private String doChatReply(String teamId, String userMessage, NlSessionStore.SessionContext session) {
        String state = teamId == null || teamId.isBlank() ? "{}" : writeJsonSafe(teamRegistryService.getState(teamId));
        return llmRetryService.executeWithRetry(() -> chatClientBuilder.build().prompt()
                .system("你是Agent Teams中文助手，回答要简洁可执行。")
                .user("状态:%s\n历史:%s\n用户:%s".formatted(state, String.join("\n", session.getHistory()), userMessage))
                .call().content());
    }

    private String buildTeamContextForIntent(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return "无团队上下文";
        }
        try {
            TeamWorkspace team = teamRegistryService.getTeam(teamId);
            TeamStateResponse state = teamRegistryService.getState(teamId);
            List<Map<String, String>> teammates = team.getTeammates().values().stream()
                    .map(t -> Map.of("id", t.getId(), "name", valueOr(t.getName(), ""), "role", valueOr(t.getRole(), "")))
                    .toList();
            List<Map<String, String>> tasks = team.getTasks().values().stream()
                    .sorted(Comparator.comparing(TeamTask::getCreatedAt))
                    .map(t -> Map.of("id", t.getId(), "title", valueOr(t.getTitle(), ""), "status", t.getStatus().name(), "assigneeId", valueOr(t.getAssigneeId(), "")))
                    .toList();
            int size = team.getMailbox().size();
            int from = Math.max(0, size - 12);
            List<Map<String, String>> recentMessages = team.getMailbox().subList(from, size).stream()
                    .map(m -> Map.of("fromId", valueOr(m.fromId(), ""), "toId", valueOr(m.toId(), ""), "content", valueOr(m.content(), "")))
                    .toList();
            return writeJsonSafe(Map.of(
                    "teamId", teamId,
                    "planVersion", state.getPlanVersion(),
                    "teammates", teammates,
                    "tasks", tasks,
                    "recentMessages", recentMessages
            ));
        } catch (Exception e) {
            return "团队上下文获取失败: " + e.getMessage();
        }
    }

    private NlIntent parseIntent(String userMessage, String teamId, NlSessionStore.SessionContext session) {
        String prompt = """
                你是Agent Teams指令解析器，只输出JSON。
                动作结构见下：CREATE_TEAM|ADD_TASK|SEND_MESSAGE|BROADCAST|RUN_TASK|GET_STATE|CHAT。
                关键约束：优先输出真实ID字段(fromId/toId/assigneeId/taskId)，只有无法确定ID时才输出name字段。
                团队上下文:%s
                会话历史:%s
                用户输入:%s
                """.formatted(buildTeamContextForIntent(teamId), String.join("\n", session.getHistory()), userMessage);
        String raw = llmRetryService.executeWithRetry(() -> chatClientBuilder.build().prompt().user(prompt).call().content());
        try {
            NlIntent intent = objectMapper.readValue(stripCodeFence(raw), NlIntent.class);
            if (intent.getActions() == null) {
                intent.setActions(new ArrayList<>());
            }
            return intent;
        } catch (JsonProcessingException e) {
            NlIntent fallback = new NlIntent();
            NlIntent.Action chat = new NlIntent.Action();
            chat.setType("CHAT");
            fallback.setActions(List.of(chat));
            fallback.setAssistantReply("结构化解析失败，切换到对话模式。");
            return fallback;
        }
    }

    private String requireTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("当前会话没有可用teamId，请先创建团队或传入teamId。");
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
                .filter(t -> name.equalsIgnoreCase(t.getName()))
                .map(TeammateAgent::getId)
                .findFirst();
        return found.orElse(null);
    }

    private String selectExecutor(TeamWorkspace team) {
        List<TeammateAgent> all = team.getTeammates().values().stream().toList();
        List<TeammateAgent> executors = all.stream().filter(t -> !isLeaderRole(t.getRole())).toList();
        if (executors.isEmpty()) {
            executors = all;
        }
        Map<String, Long> busy = team.getTasks().values().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .filter(t -> t.getAssigneeId() != null && !t.getAssigneeId().isBlank())
                .collect(Collectors.groupingBy(TeamTask::getAssigneeId, Collectors.counting()));
        return executors.stream()
                .min(Comparator.comparingLong((TeammateAgent t) -> busy.getOrDefault(t.getId(), 0L)).thenComparing(TeammateAgent::getId))
                .map(TeammateAgent::getId)
                .orElseThrow(() -> new IllegalArgumentException("没有可分配执行agent"));
    }

    private String findLeaderId(TeamWorkspace team) {
        return team.getTeammates().values().stream().filter(t -> isLeaderRole(t.getRole())).map(TeammateAgent::getId).findFirst().orElse(null);
    }

    private boolean isLeaderRole(String role) {
        if (role == null) {
            return false;
        }
        String lower = role.toLowerCase(Locale.ROOT);
        return lower.contains("leader") || lower.contains("lead") || lower.contains("manager")
                || lower.contains("planner") || lower.contains("orchestrator");
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
        public String taskId;
        public String title;
        public String description;
        public List<String> dependencyTaskIds;
        public String assigneeId;
        public String assigneeName;
        public String content;
        public String toId;
        public String toName;
    }
}
