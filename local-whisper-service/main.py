from fastapi import FastAPI, UploadFile, File, Form
from faster_whisper import WhisperModel
import tempfile
import os

app = FastAPI()

# For better Swedish transcription accuracy, start with small.
# Options: "tiny", "base", "small"
MODEL_SIZE = os.getenv("WHISPER_MODEL", "small")
HOTWORDS = os.getenv("WHISPER_HOTWORDS", "").strip() or None

# CPU-friendly settings
model = WhisperModel(
    MODEL_SIZE,
    device="cpu",
    compute_type="int8"
)

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_SIZE, "hotwordsConfigured": HOTWORDS is not None}

@app.post("/v1/audio/transcriptions")
async def transcribe(
    file: UploadFile = File(...),
    language: str | None = Form(default=None)
):
    suffix = os.path.splitext(file.filename or "audio.wav")[1] or ".wav"

    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        segments, info = model.transcribe(
            tmp_path,
            language=language,
            beam_size=5,
            hotwords=HOTWORDS,
            vad_filter=True
        )

        text = " ".join(segment.text.strip() for segment in segments).strip()

        return {
            "text": text,
            "language": info.language,
            "duration": info.duration
        }
    finally:
        os.remove(tmp_path)
