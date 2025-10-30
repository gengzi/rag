# streaming_tts.py
import os
import io
import torch
import numpy as np
from fastapi import FastAPI, Request, Form
from fastapi.responses import StreamingResponse
import wave
import asyncio
from openvoice import se_extractor
from openvoice.api import ToneColorConverter
from melo.api import TTS as MeloTTS  # OpenVoice 底层使用 MeloTTS

app = FastAPI(title="OpenVoice Streaming TTS")

# === 配置 ===
DEVICE = "cpu"  # 或 "cuda" if you have GPU
OUTPUT_SAMPLE_RATE = 16000  # OpenVoice 默认 16k

# 首次运行会自动下载模型到 ~/.cache/huggingface/hub/
print("Loading OpenVoice models...")
tts_model = MeloTTS(language="mix", device=DEVICE)  # 'mix' 支持中英混合
print("✅ Models loaded.")

def text_to_chunks(text: str) -> list:
    """简单按句分割（可替换为更智能的分句）"""
    import re
    sentences = re.split(r'(?<=[。！？.!?\n])', text)
    return [s.strip() for s in sentences if s.strip()]

def generate_wav_header(sample_rate, bits_per_sample=16, channels=1):
    """生成 WAV 文件头（用于流式播放）"""
    byte_rate = sample_rate * channels * bits_per_sample // 8
    block_align = channels * bits_per_sample // 8

    wav_header = bytearray()
    wav_header.extend(b'RIFF')
    wav_header.extend((0).to_bytes(4, 'little'))  # 后续填充
    wav_header.extend(b'WAVE')
    wav_header.extend(b'fmt ')
    wav_header.extend((16).to_bytes(4, 'little'))
    wav_header.extend((1).to_bytes(2, 'little'))  # PCM
    wav_header.extend(channels.to_bytes(2, 'little'))
    wav_header.extend(sample_rate.to_bytes(4, 'little'))
    wav_header.extend(byte_rate.to_bytes(4, 'little'))
    wav_header.extend(block_align.to_bytes(2, 'little'))
    wav_header.extend(bits_per_sample.to_bytes(2, 'little'))
    wav_header.extend(b'data')
    wav_header.extend((0).to_bytes(4, 'little'))  # 后续填充
    return bytes(wav_header)

async def tts_streamer(text: str):
    """生成器：逐句返回音频数据"""
    # 先发送 WAV 头（前端可直接播放）
    yield generate_wav_header(OUTPUT_SAMPLE_RATE)

    chunks = text_to_chunks(text)
    for chunk in chunks:
        if not chunk:
            continue
        # 同步 TTS（OpenVoice 不支持异步，用线程池可优化）
        src_se = torch.load(os.path.join(os.path.dirname(__file__), 'openvoice', 'checkpoints', 'base_speakers', 'ses', 'zh.pth'), map_location=DEVICE)
        audio_data = tts_model.tts(chunk, src_se, speed=1.0)  # shape: (T,)

        # 转为 int16 PCM
        audio_int16 = (audio_data * 32767).astype(np.int16)
        audio_bytes = audio_int16.tobytes()

        yield audio_bytes
        await asyncio.sleep(0.01)  # 让出控制权

@app.post("/tts/stream")
async def tts_stream(request: Request, text: str = Form(...)):
    """
    流式 TTS 接口
    调用示例:
      curl -X POST http://localhost:8000/tts/stream -d "text=Hello, 今天是2025年！"
    """
    if not text.strip():
        return {"error": "Text is empty"}
    return StreamingResponse(
        tts_streamer(text),
        media_type="audio/wav"
    )

@app.get("/")
def home():
    return {"message": "OpenVoice Streaming TTS Server", "endpoint": "/tts/stream (POST, form: text)"}