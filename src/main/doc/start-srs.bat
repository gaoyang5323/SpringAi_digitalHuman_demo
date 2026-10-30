

@echo off
chcp 65001 >nul
:: 先判断容器是否存在
docker inspect srs 2>nul >nul
if %errorlevel% equ 0 (
    :: 容器存在，单独拿到运行状态
    for /f %%a in ('docker inspect --format="{{.State.Running}}" srs') do set RUN_STATUS=%%a
    if "!RUN_STATUS!"=="true" (
        echo srs容器已经在运行，无需重复启动
    ) else (
        echo srs容器存在但是已停止，正在启动 srs
        docker start srs
    )
    pause
    exit /b
)
:: 容器不存在，新建
echo 正在新建并启动 srs
docker run -d --name srs --restart unless-stopped ^
  -p 1935:1935 ^
  -p 1985:1985 ^
  -p 8020:8080 ^
  -p 8000:8000/udp ^
  registry.cn-hangzhou.aliyuncs.com/ossrs/srs:5
pause
