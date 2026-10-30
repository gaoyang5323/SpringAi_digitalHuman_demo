@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

:: 判断 searxng 容器是否存在
docker inspect searxng 2>nul >nul
if %errorlevel% equ 0 (
    :: 获取运行状态
    for /f %%a in ('docker inspect --format="{{.State.Running}}" searxng') do set RUN_STATUS=%%a
    if "!RUN_STATUS!"=="true" (
        echo searxng 容器已经正在运行！
    ) else (
        echo searxng 容器已存在，正在启动...
        docker start searxng
    )
) else (
    echo 不存在searxng容器，新建并启动
    docker run -d --name searxng -p 8888:8080 searxng/searxng
)
pause
