# Python Chat API

基于 FastAPI + Ollama + PostgreSQL 的聊天 API 服务。

## 功能特性

- ✅ 同步聊天接口 (`POST /chat`)
- ✅ 流式聊天接口 (`POST /chat/stream`) - 使用 SSE
- ✅ 支持 Ollama 大模型
- ✅ PostgreSQL 数据库存储
- ✅ 自动文档 (Swagger UI)

## 安装依赖

```bash
pip install -r requirements.txt
```

## 配置环境变量

复制 `.env.example` 为 `.env` 并修改配置：

```bash
cp .env.example .env
```

主要配置项：
- `DB_HOST`: PostgreSQL 主机
- `DB_PORT`: PostgreSQL 端口
- `DB_USER`: 数据库用户名
- `DB_PASSWORD`: 数据库密码
- `OLLAMA_BASE_URL`: Ollama 服务地址
- `OLLAMA_MODEL`: 使用的模型名称

## 启动服务

```bash
# 方式1: 使用 run.py (推荐)
python run.py

# 方式2: 使用 uvicorn
uvicorn run:app --reload --host 0.0.0.0 --port 8000
```

## API 接口

### 1. 同步聊天

```bash
POST /chat
Content-Type: application/json

{
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "stream": false,
  "temperature": 0.7,
  "max_tokens": 1024
}
```

响应：
```json
{
  "role": "assistant",
  "content": "你好！有什么我可以帮助你的吗？",
  "model": "Qwen2.5:7b",
  "finish_reason": "stop"
}
```

### 2. 流式聊天

```bash
POST /chat/stream
Content-Type: application/json

{
  "messages": [
    {"role": "user", "content": "讲一个故事"}
  ],
  "temperature": 0.7
}
```

响应为 SSE 格式：
```
event: message
data: {"role":"assistant","content":"从前","model":"Qwen2.5:7b"}

event: message
data: {"role":"assistant","content":"有一个","model":"Qwen2.5:7b"}

event: done
data: {"model":"Qwen2.5:7b","finish_reason":"stop"}
```

### 3. 获取模型列表

```bash
GET /chat/models
```

### 4. 健康检查

```bash
GET /chat/health
```

## 访问文档

启动服务后访问：
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## 项目结构

```
python/
├── app/
│   ├── __init__.py
│   ├── main.py           # FastAPI 入口
│   ├── config.py         # 配置管理
│   ├── database.py       # 数据库连接
│   ├── models.py         # 数据模型
│   ├── routers/
│   │   ├── __init__.py
│   │   └── chat.py       # 聊天路由
│   └── services/
│       ├── __init__.py
│       └── ollama_service.py  # Ollama 服务
├── .env.example          # 环境变量示例
├── requirements.txt      # Python 依赖
└── README.md
```
