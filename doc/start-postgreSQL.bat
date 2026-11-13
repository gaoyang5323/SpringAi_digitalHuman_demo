@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

:: 判断 pg 容器是否存在
docker inspect pg 2>nul >nul
if %errorlevel% equ 0 (
    for /f %%a in ('docker inspect --format="{{.State.Running}}" pg') do set RUN_STATUS=%%a
    if "!RUN_STATUS!"=="true" (
        echo pg容器已经在运行，无需重复启动
    ) else (
        echo pg容器已存在，正在启动...
        docker start pg
    )
    pause
    exit /b
)

echo 不存在pg容器，新建并启动（数据持久化到 D:\soft\pgdata）
docker run -d --name pg -p 5432:5432 ^
  -e POSTGRES_USER=postgres ^
  -e POSTGRES_PASSWORD=123456 ^
  -e POSTGRES_DB=postgres ^
  -e TZ=Asia/Shanghai ^
  -v D:/soft/pgdata:/var/lib/postgresql/data ^
  ankane/pgvector
pause
