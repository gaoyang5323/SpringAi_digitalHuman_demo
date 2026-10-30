@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

cd /d D:\soft\livetalking\
python app.py --transport webrtc --model wav2lip --avatar_id wav2lip256_avatar1
pause
