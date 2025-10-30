# Dockerfile
FROM python:3.10-slim

# 设置时区 & 安装系统依赖（torchaudio 需要）
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libsndfile1 \
    ffmpeg \
    git \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 复制依赖文件
COPY requirements.txt .

# 安装 Python 依赖（使用国内源加速）
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# 👇 关键：复制你本地的 OpenVoice 文件夹
COPY OpenVoice/ ./OpenVoice/

# 从本地目录安装（-e 表示可编辑模式，方便调试）
RUN pip install -e ./OpenVoice

# 复制应用代码
COPY streaming_tts.py .

# 暴露端口
EXPOSE 8000

# 在 CMD 前添加：
RUN python -c "
import os;
os.environ['HF_HOME'] = '/app/.cache';
from melo.api import TTS;
print('Pre-downloading OpenVoice models...');
tts = TTS(language='mix', device='cpu');
print('✅ Models cached.')
"
ENV HF_HOME=/app/.cache

# 启动命令
CMD ["uvicorn", "streaming_tts:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]