FROM python:3.11-slim

WORKDIR /app

# 解决时区问题（可选，根据需要调整）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 1. 替换为国内镜像源加速下载（阿里云，适用于 Debian Bookworm）
# 2. 补充 LibreOffice 可能缺失的依赖（如字体、图形库）
# 3. 清理缓存减小镜像体积
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        libreoffice \
        libreoffice-writer \
        fonts-wqy-zenhei \
        fontconfig \
        && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 复制依赖文件并安装
COPY requirements.txt .
RUN pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple && \
    pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY ./app /app/app

# 暴露端口
EXPOSE 8050

# 启动服务（使用 uvicorn 作为服务器）
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8050"]