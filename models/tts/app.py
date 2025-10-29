import os
import tempfile
import torch
import sys
from flask import Flask, request, send_file, jsonify
sys.path.append("D:/work/tts/ZipVoice")  # 指向仓库根目录
from zipvoice.bin.infer_zipvoice import main as infer_zipvoice
import argparse

app = Flask(__name__)

# 模型目录（Docker 内部路径）
MODEL_DIR = "E:\models"
os.makedirs(MODEL_DIR, exist_ok=True)

# 检查并下载预训练模型（首次启动时执行）
def download_model():
    if not os.path.exists(f"{MODEL_DIR}/model.pt"):
        print("下载预训练模型...")
        from huggingface_hub import hf_hub_download
        hf_hub_download(
            repo_id="k2-fsa/ZipVoice",
            filename="zipvoice/model.pt",
            local_dir=MODEL_DIR,
            local_dir_use_symlinks=False
        )
        hf_hub_download(
            repo_id="k2-fsa/ZipVoice",
            filename="zipvoice/tokens.txt",
            local_dir=MODEL_DIR,
            local_dir_use_symlinks=False
        )
        hf_hub_download(
            repo_id="k2-fsa/ZipVoice",
            filename="zipvoice/model.json",
            local_dir=MODEL_DIR,
            local_dir_use_symlinks=False
        )
    print("模型准备就绪")

# 初始化模型
download_model()

@app.route('/tts', methods=['POST'])
def tts():
    """
    文本转语音 API
    请求体参数：
    - text: 待转换的文本（必填）
    - prompt_wav: 参考语音文件（可选，用于克隆音色，默认使用内置语音）
    - prompt_text: 参考语音对应的文本（可选，与 prompt_wav 配套）
    - speed: 语速（可选，默认 1.0）
    """
    data = request.json
    if not data or "text" not in data:
        return jsonify({"error": "缺少文本参数"}), 400

    # 临时文件存储输入输出
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as out_wav:
        output_path = out_wav.name

    # 构建推理参数
    args = argparse.Namespace(
        model_name="zipvoice",
        model_dir=MODEL_DIR,
        checkpoint_name="model.pt",
        prompt_wav=data.get("prompt_wav", "egs/zipvoice/egs/emilia/prompt.wav"),  # 内置参考语音
        prompt_text=data.get("prompt_text", "Hello, this is a prompt."),
        text=data["text"],
        res_wav_path=output_path,
        speed=data.get("speed", 1.0),
        remove_long_sil=True  # 去除长静音
    )

    try:
        # 调用 ZipVoice 生成语音
        infer_zipvoice(args)
        # 返回生成的音频文件
        return send_file(output_path, mimetype="audio/wav", as_attachment=True, download_name="output.wav")
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        # 清理临时文件
        if os.path.exists(output_path):
            os.remove(output_path)

if __name__ == '__main__':
    # 允许外部访问，端口 5000
    app.run(host='0.0.0.0', port=5000, debug=False)
