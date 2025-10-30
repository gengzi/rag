# 使用支持 CUDA 11.8 的基础镜像（匹配 torch 和 k2 的 CUDA 版本）
FROM nvidia/cuda:11.8.0-cudnn8-runtime-ubuntu22.04

# 设置非交互模式，避免 apt 安装时的交互提示
ENV DEBIAN_FRONTEND=noninteractive
# 替换为阿里云 Ubuntu 源（解决国内访问问题）
RUN sed -i s@/archive.ubuntu.com/@/mirrors.aliyun.com/@g /etc/apt/sources.list && \
    sed -i s@/security.ubuntu.com/@/mirrors.aliyun.com/@g /etc/apt/sources.list
# 安装 Python 及系统依赖（包含音频处理所需的 libsndfile1）
# 分步安装，每步单独执行，便于定位错误
RUN apt-get update --fix-missing

# 先安装基础系统工具
RUN apt-get install -y --no-install-recommends \
    gcc \
    git \
    && rm -rf /var/lib/apt/lists/*

# 再安装 Python 相关
RUN apt-get update --fix-missing && \
    apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-dev \
    && rm -rf /var/lib/apt/lists/*

# 最后安装音频依赖
RUN apt-get update --fix-missing && \
    apt-get install -y --no-install-recommends \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# 安全创建软链接（核心修改）
RUN if [ -f "/usr/bin/python3" ] && [ ! -f "/usr/bin/python" ]; then \
        ln -s /usr/bin/python3 /usr/bin/python; \
    fi && \
    if [ -f "/usr/bin/pip3" ] && [ ! -f "/usr/bin/pip" ]; then \
        ln -s /usr/bin/pip3 /usr/bin/pip; \
    fi

WORKDIR /app

# 安装系统依赖（如需要）
#RUN apt-get update && apt-get install -y \
#    libsndfile1 \
#    && rm -rf /var/lib/apt/lists/*

# 复制依赖
COPY requirements.txt .
COPY ./ZipVoice/requirements.txt /app/requirements_api.txt
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
RUN pip install --no-cache-dir -r /app/requirements_api.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
RUN pip install torch==2.3.0 --index-url https://download.pytorch.org/whl/cu118
RUN pip install k2==1.24.4.dev20241029+cuda11.8.torch2.3.0 -f https://k2-fsa.github.io/k2/cuda-cn.html

# 复制代码
COPY ./ZipVoice /app/ZipVoice
COPY main.py .

# 暴露端口
EXPOSE 8080

# 启动服务
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080", "--workers", "1"]