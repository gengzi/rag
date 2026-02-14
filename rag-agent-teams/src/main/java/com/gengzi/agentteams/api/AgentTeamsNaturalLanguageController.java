package com.gengzi.agentteams.api;

import com.gengzi.agentteams.nl.NlAgentTeamsService;
import com.gengzi.agentteams.nl.NlChatRequest;
import com.gengzi.agentteams.nl.NlChatResponse;
import com.gengzi.agentteams.nl.NlWorkflowEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Tag(name = "Agent Teams NL", description = "自然语言创建/运行/对话接口，支持实时工作流推送")
@RestController
@RequestMapping("/api/teams/nl")
public class AgentTeamsNaturalLanguageController {

    private final NlAgentTeamsService nlAgentTeamsService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AgentTeamsNaturalLanguageController(NlAgentTeamsService nlAgentTeamsService) {
        this.nlAgentTeamsService = nlAgentTeamsService;
    }

    @Operation(summary = "自然语言同步执行", description = "通过一段自然语言完成团队创建、任务运行、对话，并返回最终状态")
    @PostMapping("/chat")
    public NlChatResponse chat(@Valid @RequestBody NlChatRequest request) {
        return nlAgentTeamsService.handle(request, event -> {
            // 同步接口不向客户端实时推送，仅收集在返回体中
        });
    }

    @Operation(summary = "自然语言流式执行", description = "SSE 实时返回工作流事件，最后会返回 result 事件（包含最终响应）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody NlChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        executor.submit(() -> {
            try {
                NlChatResponse response = nlAgentTeamsService.handle(request, event -> sendEvent(emitter, "workflow", event));
                sendEvent(emitter, "result", response);
                emitter.complete();
            } catch (Exception ex) {
                sendEvent(emitter, "error", NlWorkflowEvent.of("ERROR", ex.getMessage(), null));
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }
}
