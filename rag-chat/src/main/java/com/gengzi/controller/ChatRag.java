package com.gengzi.controller;


import com.gengzi.request.TtsReq;
import com.gengzi.service.TtsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController("/chat/flux")
@Tag(name = "rag对话聊天涉及的相关接口", description = "rag对话聊天涉及的相关接口")
public class ChatRag {
    private static final Logger logger = LoggerFactory.getLogger(ChatRag.class);

    @Autowired
    private TtsService ttsService;

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
