@echo off
chcp 65001 >nul
:: 先判断容器是否存在
docker inspect redis 2>nul >nul
if %errorlevel% equ 0 (
    :: 容器存在，单独拿到运行状态
    for /f %%a in ('docker inspect --format="{{.State.Running}}" redis') do set RUN_STATUS=%%a
    if "!RUN_STATUS!"=="true" (
        echo redis容器已经在运行，无需重复启动
    ) else (
        echo redis容器存在但是已停止，正在启动 redis
        docker start redis
    )
    pause
    exit /b
)
:: 容器不存在，新建
echo 正在新建并启动 redis
docker run -d --name redis  -p 6379:6379 redis redis-server --requirepass 123456 --appendonly yes
pause
