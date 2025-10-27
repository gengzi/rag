import requests
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import FileResponse, PlainTextResponse
import os
import io
import tempfile
import requests
from starlette.responses import StreamingResponse
from pydantic import BaseModel
from .converter import convert_to_markdown

class Markdown(BaseModel):
    url: str

app = FastAPI(title="MarkItDown API", description="Word/PDF 转 Markdown 服务")

# 临时目录存储上传文件和转换结果
UPLOAD_DIR = tempfile.mkdtemp()
os.makedirs(UPLOAD_DIR, exist_ok=True)

@app.post("/convert/fileurl", summary="通过文件url获取文件并转换为 Markdown")
async def convert_file(
        Markdown: Markdown
        # url: str,
        # return_content: bool = False  # 是否直接返回 Markdown 内容（而非文件下载）
):
    ## 通过url下载文件内容，存放到临时目录
    try:
        # 调用转换函数
        md_content = convert_to_markdown(Markdown.url)

        # if return_content:
        #     # 直接返回 Markdown 文本内容
        #     with open(md_content, "r", encoding="utf-8") as f:
        #         content = f.read()
        #     return PlainTextResponse(content=content, media_type="text/markdown")
        # else:
        md_bytes = md_content.encode("utf-8")
        md_content_stream = io.BytesIO(md_bytes)
        # 推荐：添加文件名，让客户端下载时自动使用该名称
        headers = {
            "Content-Disposition": "attachment; filename=\"result.md\""
        }
        # 返回文件下载
        return StreamingResponse(
            md_content_stream,
            media_type="text/markdown",
            status_code=200,
            headers=headers
        )
    except Exception as e:
        print(e)
        raise HTTPException(status_code=500, detail=f"转换失败：{str(e)}")


@app.get("/health", summary="健康检查")
async def health_check():
    return {"status": "healthy", "service": "markitdown-api"}


# if __name__ == "__main__":
#     import uvicorn
#
#     uvicorn.run("app.main:app", host="0.0.0.0", port=8050)
