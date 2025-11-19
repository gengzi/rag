package com.gengzi.controller;


import com.gengzi.request.ChatMsgRecordReq;
import com.gengzi.request.ChatReq;
import com.gengzi.request.TtsReq;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.ConversationDetailsResponse;
import com.gengzi.service.ChatService;
import com.gengzi.service.TtsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController()
@RequestMapping("/chat")
@Tag(name = "rag对话聊天涉及的相关接口", description = "rag对话聊天涉及的相关接口")
public class ChatRag {
    private static final Logger logger = LoggerFactory.getLogger(ChatRag.class);

    @Autowired
    private TtsService ttsService;

    @Autowired
    private ChatService chatService;

    /**
     * 聊天对话接口
     * 前端交互，返回对话内容信息
     */
    @PostMapping(value = "/stream/msg", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatMessageResponse>> chat(@RequestBody ChatReq req) {
        return chatService.chat(req);
    }



    /**
     * 聊天记录分页查询接口
     * 每次分页50条，从后往前查
     */
    @PostMapping(value = "/msg/{conversationId}/list")
    public Mono<ConversationDetailsResponse> chatMsgList(
            @PathVariable String conversationId,
            @RequestBody ChatMsgRecordReq recordReq) {
        return chatService.chatMsgList(conversationId, recordReq);
    }



    /**
     * 所有agent能力列表
     * 用于查询现在支持的agent能力
     */



    /**
     * 接收前端文本请求，返回流式音频给前端
     */
    @PostMapping(value = "/stream-tts", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Flux<DataBuffer>> streamTTS(@RequestBody TtsReq request) {
        // 1. 从 TTS 客户端获取二进制音频流（Flux<byte[]>）转为DataBuffer
        Flux<DataBuffer> audioByteFlux = ttsService.getChatTTSByChatId(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audioByteFlux);
    }


}
