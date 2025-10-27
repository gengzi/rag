from fastapi import FastAPI
from pydantic import BaseModel
from transformers import AutoModelForSequenceClassification, AutoTokenizer
import torch
import os

os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"  # 国内镜像（可选）

app = FastAPI()
model_name = "jinaai/jina-reranker-v2-base-multilingual"

tokenizer = AutoTokenizer.from_pretrained(
    model_name,
    trust_remote_code=True
)
model = AutoModelForSequenceClassification.from_pretrained(
    model_name,
    trust_remote_code=True,
    dtype=torch.float16,  # 关键：用 dtype 替代 deprecated 的 torch_dtype
    device_map="auto"
)
model.eval()

class RerankRequest(BaseModel):
    query: str
    documents: list[str]

@app.post("/rerank")
def rerank(request: RerankRequest):
    pairs = [[request.query, doc] for doc in request.documents]
    inputs = tokenizer(pairs, padding=True, truncation=True, return_tensors="pt").to(model.device)
    with torch.no_grad():
        outputs = model(** inputs)
    scores = outputs.logits.squeeze().tolist()
    return [{"document": doc, "score": score} for doc, score in zip(request.documents, scores)]