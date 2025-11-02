package com.gengzi.service.impl;


import cn.hutool.json.JSONUtil;
import com.gengzi.dao.Conversation;
import com.gengzi.dao.repository.ConversationRepository;
import com.gengzi.entity.RagChatMessage;
import com.gengzi.request.TtsReq;
import com.gengzi.request.TtsStreaming;
import com.gengzi.response.BusinessException;
import com.gengzi.response.ResultCode;
import com.gengzi.service.TtsService;
import com.gengzi.utils.MdTextCleaner;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class TtsServiceImpl implements TtsService {

    private static final Logger logger = LoggerFactory.getLogger(TtsServiceImpl.class);
    // 数据缓冲区工厂（用于将 byte[] 转换为 DataBuffer）
    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    @Autowired
    private WebClient webClient;
    @Autowired
    private ConversationRepository conversationRepository;
    @Value("${model.tts.url}")
    private String ttsUrl;

    @Override
    public Flux<DataBuffer> getChatTTSByChatId(TtsReq request) {
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
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class);  // 分块接收 Base64 字符串

        // 2. 实时解码 Base64 为二进制音频流（byte[]）
        return base64Flux
                .map(chunk -> {
                    byte[] decode = Base64.getDecoder().decode(chunk.trim());
                    byte[] wavBytes = addWavHeader(decode, 24000, 16, 1);
                    return dataBufferFactory.wrap(wavBytes);
                })  // 解码每个分块
                .doOnComplete(() -> logger.info("TTS 流式处理完成"))
                .doOnError(error -> {
                    logger.error("TTS 错误：{}", error.getMessage(), error);
                    throw new BusinessException(ResultCode.TTS_ERROR);
                });
    }


    // 核心：为 PCM 数据添加 WAV 头部（确保前端可播放）
    private byte[] addWavHeader(byte[] pcmData, int sampleRate, int bitsPerSample, int channels) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // WAV 头总长度：44 字节（标准 PCM WAV 头）
            // 1. RIFF 标识
            out.write("RIFF".getBytes());
            // 2. 文件总长度（RIFF 标识 + 4 + 剩余长度）
            int fileSize = 36 + pcmData.length;
            out.write(intToBytes(fileSize));
            // 3. WAVE 标识
            out.write("WAVE".getBytes());
            // 4. fmt 子块标识
            out.write("fmt ".getBytes());
            // 5. fmt 子块长度（16 表示 PCM 格式）
            out.write(intToBytes(16));
            // 6. 音频格式（1 表示 PCM）
            out.write(shortToBytes((short) 1));
            // 7. 声道数
            out.write(shortToBytes((short) channels));
            // 8. 采样率
            out.write(intToBytes(sampleRate));
            // 9. 字节率（采样率 * 声道数 * 位深/8）
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            out.write(intToBytes(byteRate));
            // 10. 块对齐（声道数 * 位深/8）
            short blockAlign = (short) (channels * bitsPerSample / 8);
            out.write(shortToBytes(blockAlign));
            // 11. 位深
            out.write(shortToBytes((short) bitsPerSample));
            // 12. data 子块标识
            out.write("data".getBytes());
            // 13. 音频数据长度
            out.write(intToBytes(pcmData.length));
            // 14. 写入 PCM 数据
            out.write(pcmData);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("生成 WAV 头失败", e);
        }
    }

    // 辅助方法：int 转 4 字节（小端序）
    private byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    // 辅助方法：short 转 2 字节（小端序）
    private byte[] shortToBytes(short value) {
        return ByteBuffer.allocate(2).order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort(value).array();
    }

    // 模拟：从 TTS 服务获取 PCM 字节流（实际需替换为 PaddleSpeech 解码逻辑）
    private byte[] getPcmBytesFromTTS() {
        // 此处仅为示例，实际需通过 Base64 解码 PaddleSpeech 返回的音频数据
        return new byte[0];
    }

}
