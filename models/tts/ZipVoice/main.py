from io import BytesIO

import sys
import os

# 手动指定 ZipVoice 源码中 zipvoice 文件夹的绝对路径
# 请根据你的实际路径修改！例如：
ZIPVOICE_PATH = r"/app/ZipVoice"  # 假设 zipvoice 文件夹在这个目录下

# 将路径添加到 Python 模块搜索路径
if ZIPVOICE_PATH not in sys.path:
    sys.path.insert(0, ZIPVOICE_PATH)

# 测试是否能导入（可选，用于验证）
try:
    import zipvoice
    print("ZipVoice 模块导入成功")
except ImportError as e:
    print(f"ZipVoice 模块导入失败: {e}")

import uvicorn
from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import FileResponse
import subprocess
import os
import uuid
import tempfile

from starlette.responses import StreamingResponse

app = FastAPI(title="ZipVoice TTS API")

# 创建临时目录
os.makedirs("temp", exist_ok=True)


@app.post("/tts/single")
async def tts_single(
        text: str = Form(...),
        model_name: str = Form("zipvoice_distill"),
        speed: float = Form(1.0)
):
    # 生成唯一ID
    uid = str(uuid.uuid4())
    prompt_path = f"temp/audio.wav"
    output_path = f"temp/{uid}_output.wav"


    # 构建命令
    cmd = [
        "python", "-m", "zipvoice.bin.infer_zipvoice",
        "--model-name", model_name,
        "--prompt-wav", prompt_path,
        "--prompt-text", "PALM公司的执行主席JONRUBENSTEIN表示。",
        "--text", text,
        "--res-wav-path", output_path,
        "--speed", str(speed)
    ]
    # 复制当前环境变量，并添加 PYTHONPATH
    new_env = os.environ.copy()
    # 将 ZIPVOICE_PATH 添加到 PYTHONPATH（确保子进程能找到模块）
    new_env["PYTHONPATH"] = ZIPVOICE_PATH + os.pathsep + new_env.get("PYTHONPATH", "")

    # 执行命令
    result = subprocess.run(cmd, capture_output=True, text=True,env=new_env)
    if result.returncode != 0:
        return {"error": result.stderr}

    # 返回生成的音频文件
    # 读取生成的音频文件到内存缓冲区
    with open(output_path, "rb") as f:
        audio_data = f.read()

    os.remove(output_path)

    # 返回音频数据流
    return StreamingResponse(
        BytesIO(audio_data),  # 将二进制数据转为可迭代的字节流
        media_type="audio/wav",
        headers={"Content-Disposition": 'attachment; filename="output.wav"'}  # 可选：指定下载文件名
    )


# @app.post("/tts/dialog")
# async def tts_dialog(
#         test_list: UploadFile = File(...),
#         model_name: str = Form("zipvoice_dialog"),
#         res_dir: str = Form("temp/dialog_results")
# ):
#     # 创建结果目录
#     os.makedirs(res_dir, exist_ok=True)
#
#     # 保存测试列表
#     test_list_path = f"temp/{str(uuid.uuid4())}_test.tsv"
#     with open(test_list_path, "wb") as f:
#         f.write(await test_list.read())
#
#     # 构建命令
#     cmd = [
#         "python3", "-m", "zipvoice.bin.infer_zipvoice_dialog",
#         "--model-name", model_name,
#         "--test-list", test_list_path,
#         "--res-dir", res_dir
#     ]
#
#     # 执行命令
#     result = subprocess.run(cmd, capture_output=True, text=True)
#     if result.returncode != 0:
#         return {"error": result.stderr}
#
#     # 返回结果目录中的文件列表
#     files = os.listdir(res_dir)
#     return {"files": files, "directory": res_dir}


@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "ZipVoice TTS API"}

# if __name__ == "__main__":
#     uvicorn.run(app, host="0.0.0.0", port=8000)