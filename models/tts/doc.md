```shell
## docker 运行
docker run --gpus all --name paddle -it  -p 8092:8092  -v F:\tts:/paddle ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddle:3.2.0-gpu-cuda12.6-cudnn9.5 /bin/bash
# 升级 pip
pip install -U pip
# 安装 PaddleSpeech（包含核心功能和服务组件）
pip install paddlespeech
# 安装带 ONNX 支持的版本（用于流式 TTS 的 onnx 引擎）
pip install paddlespeech[onnx]

## 准备配置文件
config.yml
## 启动
paddlespeech_server start  --config_file /paddle/config.yml




其他命令
docker exec -it paddle /bin/bash  # 进入容器终端

docker exec -d paddle /bin/bash -c   /paddle/start.sh  # 后台执行脚本











```