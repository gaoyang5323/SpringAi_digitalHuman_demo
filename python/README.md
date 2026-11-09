# Python Chat API

基于 **FastAPI + LangChain + LangGraph + Ollama + PostgreSQL** 的智能聊天 API 服务。

## 功能特性

- ✅ **LangChain 集成** - 使用 LangChain Ollama 进行大模型调用
- ✅ **LangGraph 工作流** - 智能对话流程编排
- ✅ **PostgreSQL 上下文记忆** - 自动保存和加载对话历史
- ✅ 同步聊天接口 (`GET /chat`)
- ✅ 流式聊天接口 (`GET /chat/stream`) - 使用 SSE
- ✅ 会话管理接口 - 列表/删除/清空历史
- ✅ PostgreSQL 数据库存储（异步）
- ✅ 自动文档 (Swagger UI)

## 项目架构

```
┌─────────────────────────────────────────────────────────────┐
│                      FastAPI Router                          │
│        /chat  /chat/stream  /chat/sessions  ...              │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    LangGraph 工作流                          │
│    ┌──────────┐    ┌──────────┐    ┌──────────┐            │
│    │  START   │───▶│  load    │───▶│   chat   │            │
│    └──────────┘    │ history  │    └──────────┘            │
│                    └──────────┘         │                   │
│                                         ▼                   │
│                    ┌──────────┐    ┌──────────┐            │
│                    │   END    │◀───│  save    │            │
│                    └──────────┘    │ history  │            │
│                                    └──────────┘            │
└───────────────────────────┬─────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
┌──────────────────────────┐  ┌──────────────────────────────┐
│   PostgreSQL 对话历史    │  │     LangChain Ollama 服务    │
│   - chat_sessions        │  │     - ChatOllama             │
│   - chat_messages        │  │     - 同步/流式输出          │
└──────────────────────────┘  └──────────────────────────────┘
```

## 安装依赖

```bash
cd python
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
# 新对话
GET /chat?message=你好

# 延续对话（传入 session_id）
GET /chat?message=我叫小明&session_id=xxx-xxx-xxx
```

参数说明：
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| message | string | 必填 | 用户消息 |
| temperature | float | 0.7 | 温度参数 |
| max_tokens | int | 1024 | 最大token数 |
| session_id | string | null | 会话ID，传入可延续历史对话 |

响应：
```json
{
  "role": "assistant",
  "content": "你好！有什么我可以帮助你的吗？",
  "model": "Qwen2.5:7b",
  "finish_reason": "stop",
  "total_tokens": 50,
  "session_id": "abc-123-def"
}
```

### 2. 流式聊天

```bash
GET /chat/stream?message=讲一个故事
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

### 3. 会话管理

```bash
# 获取会话列表
GET /chat/sessions?limit=50&offset=0

# 获取指定会话的历史消息
GET /chat/sessions/{session_id}/history?limit=20

# 更新会话标题
PUT /chat/sessions/{session_id}/title?title=新标题

# 清空会话历史
DELETE /chat/sessions/{session_id}/history

# 删除会话
DELETE /chat/sessions/{session_id}
```

### 4. 获取模型列表

```bash
GET /chat/models
```

### 5. 健康检查

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
│   ├── main.py              # FastAPI 入口
│   ├── config.py            # 配置管理
│   ├── database.py          # PostgreSQL 异步连接
│   ├── models.py            # SQLAlchemy 数据模型
│   ├── routers/
│   │   ├── __init__.py
│   │   └── chat.py          # /chat 路由
│   └── services/
│       ├── __init__.py
│       ├── langchain_ollama_service.py  # LangChain Ollama 服务
│       ├── langgraph_chat.py          # LangGraph 工作流
│       └── chat_history_service.py    # 对话历史服务 (PostgreSQL)
├── .env.example
├── requirements.txt
├── run.py                   # 启动入口
└── README.md
```

## 核心组件

### LangChain Ollama 服务
- 使用 `langchain-ollama` 进行模型调用
- 支持同步和流式输出
- 自动消息格式转换

### LangGraph 工作流
- `ChatGraph`: 同步聊天工作流（带记忆）
- `StreamChatGraph`: 流式聊天工作流（带记忆）
- 工作流节点：`load_history` → `chat` → `save_history`
- 自动加载最近 20 条消息作为上下文

### PostgreSQL 对话历史
- `ChatSession`: 会话表（session_id, title, created_at, updated_at）
- `ChatMessage`: 消息表（session_id, role, content, model, created_at）
- 支持多会话管理
- 异步数据库操作

## 使用示例

### 1. 开始新对话
```bash
curl "http://localhost:8000/chat?message=你好"
# 返回 session_id: "abc-123"
```

### 2. 延续对话（带上下文）
```bash
curl "http://localhost:8000/chat?message=我叫小明&session_id=abc-123"
# AI 会记住之前的对话
```

### 3. 查看历史会话
```bash
curl "http://localhost:8000/chat/sessions"
```

### 4. 获取某会话的历史消息
```bash
curl "http://localhost:8000/chat/sessions/abc-123/history"
```

## 扩展开发

### 添加新节点

```python
# 在 langgraph_chat.py 中添加
async def my_custom_node(state: ChatState) -> dict:
    # 自定义逻辑
    return {"messages": [...]}

# 添加到工作流
workflow.add_node("custom", my_custom_node)
```

### 添加工具调用

```python
from langchain_core.tools import tool

@tool
def search(query: str) -> str:
    """搜索工具"""
    return "搜索结果"

# 绑定到 LLM
llm_with_tools = llm.bind_tools([search])
```
