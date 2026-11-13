@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

pip install fastapi uvicorn faster-whisper python-multipart
uvicorn faster-whisper:app --host 0.0.0.0 --port 8000
pause
