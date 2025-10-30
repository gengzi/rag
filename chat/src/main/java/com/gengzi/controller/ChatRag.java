package com.gengzi.controller;


import com.gengzi.request.TtsReq;
import com.gengzi.service.TtsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

@RestController
@Tag(name = "rag对话", description = "rag对话")
public class ChatRag {
    private static final Logger logger = LoggerFactory.getLogger(ChatRag.class);
    // 数据缓冲区工厂（用于将 byte[] 转换为 DataBuffer）
    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    @Autowired
    private TtsService ttsService;

    /**
     * 接收前端文本请求，返回流式音频给前端
     */
    @PostMapping(value = "/webflux/chat/stream-tts", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Flux<DataBuffer>> streamTTS(@RequestBody TtsReq request) {
        // 1. 从 TTS 客户端获取二进制音频流（Flux<byte[]>）
        Flux<byte[]> audioByteFlux = ttsService.getChatTTSByChatId(request);
        logger.info("开始获取音频流");
        // 2. 将 byte[] 转换为 DataBuffer（WebFlux 响应流要求的格式）
        Flux<DataBuffer> dataBufferFlux = audioByteFlux
                .map(bytes -> {
                    byte[] wavBytes = addWavHeader(bytes, 24000, 16, 1);
                    return dataBufferFactory.wrap(wavBytes);
                })  // 转换为 DataBuffer
                .doOnComplete(() -> {
                    // 音频发送完毕
                    logger.info("音频发送完毕");
                });
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(dataBufferFlux);
    }


    /**
     * 接收前端文本请求，返回流式音频给前端
     */
    @PostMapping(value = "/chat/stream-tts", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> streamTTSV2(@RequestBody TtsReq request) throws IOException {
        // 1. 从 TTS 客户端获取二进制音频流（Flux<byte[]>）
        Flux<byte[]> audioByteFlux = ttsService.getChatTTSByChatId(request);
        List<byte[]> block = audioByteFlux.collectList().block(Duration.ofSeconds(10000));
        int totalLength = block.stream().mapToInt(b -> b.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        block.stream().forEach(
                v -> {
                    buffer.put(v);
                }
        );
        byte[] bytes = buffer.array();
        // 2. 为 PCM 数据添加 WAV 头（关键：前端无法播放裸 PCM，必须加头）
        byte[] wavBytes = addWavHeader(bytes, 24000, 16, 1); // 采样率 24000Hz，16bit，单声道
        Files.write(Paths.get("F:\\tts\\test.wav"), wavBytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav")) // 设置响应类型（如音频流）
                .body(wavBytes);
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
