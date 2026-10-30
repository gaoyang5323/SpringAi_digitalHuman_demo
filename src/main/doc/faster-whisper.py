from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from pydantic import BaseModel
from typing import Optional
import os, tempfile, json
from faster_whisper import WhisperModel

app = FastAPI(title="Faster-Whisper ASR")

MODEL_NAME = os.getenv("ASR_MODEL", "base")
DEVICE = os.getenv("ASR_DEVICE", "cpu")
COMPUTE_TYPE = os.getenv("ASR_COMPUTE_TYPE", "int8")

model = WhisperModel(MODEL_NAME, device=DEVICE, compute_type=COMPUTE_TYPE)

@app.get("/v1/models")
def list_models():
    return {
        "object": "list",
        "data": [
            {
                "id": MODEL_NAME,
                "object": "model",
                "created": 0,
                "owned_by": "faster-whisper",
            }
        ],
    }

@app.post("/v1/audio/transcriptions")
async def openai_transcribe(
    file: UploadFile = File(...),
    model_name: Optional[str] = Form(None),  # 改名，避免覆盖全局 model
    language: Optional[str] = Form(None),
    response_format: Optional[str] = Form("json"),
):
    if not file.content_type or not file.content_type.startswith("audio"):
        raise HTTPException(400, "只支持音频文件")

    with tempfile.NamedTemporaryFile(delete=False) as tmp:
        tmp.write(await file.read())
        tmp_path = tmp.name

    try:
        # 这里 model 仍然是全局的 WhisperModel 对象
        segments, info = model.transcribe(
            tmp_path,
            language=language,
            vad_filter=True,
            beam_size=5,
        )

        text = " ".join(seg.text for seg in segments)

        if response_format == "text":
            return text

        return {
            "text": text,
            "language": info.language,
            "segments": [
                {"start": s.start, "end": s.end, "text": s.text}
                for s in segments
            ],
        }
    finally:
        os.remove(tmp_path)

@app.post("/transcribe")
async def transcribe(file: UploadFile = File(...), language: str | None = None):
    if not file.content_type or not file.content_type.startswith("audio"):
        raise HTTPException(400, "只支持音频文件")

    with tempfile.NamedTemporaryFile(delete=False) as tmp:
        tmp.write(await file.read())
        tmp_path = tmp.name

    try:
        segments, info = model.transcribe(
            tmp_path,
            language=language,
            vad_filter=True,
            beam_size=5,
        )

        text = " ".join(seg.text for seg in segments)

        return {
            "text": text,
            "language": info.language,
            "segments": [
                {"start": s.start, "end": s.end, "text": s.text}
                for s in segments
            ],
        }
    finally:
        os.remove(tmp_path)