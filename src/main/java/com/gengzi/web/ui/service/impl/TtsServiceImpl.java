package com.gengzi.web.ui.service.impl;

import cn.hutool.json.JSONUtil;
import com.gengzi.dao.Conversation;
import com.gengzi.dao.entity.RagChatMessage;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.request.TtsReq;
import com.gengzi.request.TtsStreaming;
import com.gengzi.response.ResultCode;
import com.gengzi.utils.MarkdownCleaner;
import com.gengzi.utils.MdTextCleaner;
import com.gengzi.web.security.BusinessException;
import com.gengzi.web.ui.service.TtsService;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class TtsServiceImpl implements TtsService {

    private static final Logger logger = LoggerFactory.getLogger(TtsServiceImpl.class);
    @Autowired
    private WebClient webClient;

    @Autowired
    private ConversationRepository conversationRepository;

    @Value("${model.tts.url}")
    private String ttsUrl;

    @Override
    public Flux<byte[]> getChatTTSByChatId(TtsReq request) {
        // 查询数据库获取对应文本内容
        Optional<Conversation> byId = conversationRepository.findById(request.getConversationId());
        if (!byId.isPresent()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        String message = byId.get().getMessage();
        List<RagChatMessage> list = JSONUtil.toList(message, RagChatMessage.class);
        Optional<RagChatMessage> ttsText = list.stream().filter(ragChatMessage ->
                        MessageType.ASSISTANT.name().equals(ragChatMessage.getRole()) && ragChatMessage.getId().equals(request.getChatId()))
                .findFirst();
        if (!ttsText.isPresent()) {
            return Flux.empty();
        }
        String text = ttsText.get().getContent();
        if (StringUtils.isBlank(text)) {
            return Flux.empty();
        }
        TtsStreaming build = TtsStreaming.builder().text(MdTextCleaner.cleanMdText(text.trim())).spk_id(0).build();
        String requestBody = JSONUtil.toJsonStr(build);
        // 1. 调用接口，获取 Base64 分块流（Flux<String>）
        Flux<String> base64Flux = webClient.post()
                .uri(ttsUrl.trim())  // 流式接口路径
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class);  // 分块接收 Base64 字符串

        // 2. 实时解码 Base64 为二进制音频流（byte[]）
        return base64Flux
                .map(chunk -> Base64.getDecoder().decode(chunk.trim()))  // 解码每个分块
                .doOnComplete(() -> logger.info("TTS 流式处理完成"))
                .doOnError(error -> {
                    logger.error("TTS 错误：{}", error.getMessage(), error);
                    throw new BusinessException(ResultCode.TTS_ERROR);
                });
    }


}
