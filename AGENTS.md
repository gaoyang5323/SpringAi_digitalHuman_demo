# AGENTS.md

数字人 Demo 混合仓库：Java Spring AI 后端（仓库根目录）+ Python FastAPI/LangGraph 聊天服务（`python/`）+ Vue 3 录音前端（`frontend/`）。三端相互独立，共用本地 PostgreSQL 与 Ollama。

## 技术栈与版本

- Java 后端：Spring Boot 4.1.1 / Java 25 / Spring AI 2.0.0-M1 / Spring AI Alibaba 2.0.0-M1.1（Maven，带 `mvnw`）
- Python：FastAPI + LangChain + LangGraph + SQLAlchemy(asyncio) + asyncpg
- 前端：Vue 3 + Vite 6（`vue-asr-client`）

## 常用命令

Windows 环境下 Maven 用 `mvnw.cmd`，Git Bash 用 `./mvnw`。

```bash
# Java 后端（仓库根目录，端口 8080）
./mvnw spring-boot:run
./mvnw test                  # 集成测试，需本地 PostgreSQL/Ollama 等服务就绪
./mvnw -DskipTests package

# Python 聊天服务（虚拟环境在仓库根 .venv，不在 python/ 下）
cd python
../.venv/Scripts/python -m app.main          # 入口 app.main:app
# 或：uvicorn app.main:app --reload --port 8001
../.venv/Scripts/pip install -r requirements.txt

# 前端（端口 3000，/upload 与 /digital-human 代理到 localhost:8080）
cd frontend && npm install && npm run dev
```

## 外部依赖服务（本地需先启动）

| 服务 | 默认地址 | 用途 |
|---|---|---|
| PostgreSQL (pgvector) | localhost:5432 | 业务表、chat memory、向量库（768 维 HNSW/COSINE） |
| Redis | 127.0.0.1:6379 | 缓存 / Redisson |
| Ollama | localhost:11434 | chat: Qwen2.5:7b；embedding: nomic-embed-text |
| LiveTalking | localhost:8010 | 数字人 WebRTC 驱动 |
| Speaches | localhost:8000 | ASR/TTS（faster-distil-whisper-large-v3） |
| SearXNG | localhost:8888 | Web 搜索 |

## 架构要点

Java 主包 `com.kakuiwong.gaoyangspringai`：

- `ai/tools/`：`@Tool` 工具集（天气、本地生活、知识搜索、向量库、Web 搜索、Computer、人工反馈等）
- `ai/agent/`、`service/graph/`、`config/GraphConfig`：Spring AI Alibaba Graph 编排（ReactAgent、CompiledGraph、多 Agent handoff）
- `mcp/server/`：本进程即 MCP Server（SSE 传输，`/sse` + `/mcp/messages`，`McpAuthFilter` 做 Bearer 鉴权）；`mcp/client/`：Streamable HTTP 连外部 MCP
- `controller/digitalHuman/`：`/digital-human/offer|speak|is-speaking|session/{id}` 对接 LiveTalking；`/upload/asr` 接收录音 → Speaches 转写 → 数字人复读
- `entity/` + `mapper/`：MyBatis-Plus（RequestTask、SpringAiChatMemory）；对话记忆走 Spring AI JDBC repository
- `util/`：LiveTalkingUtil、SpeachesUtil、ThreadPoolUtil

Python（`python/app/`）：入口 `main.py`；LangGraph 工作流 `load_history → chat → save_history`；`ChatSession`/`ChatMessage` 存 PostgreSQL；路由 `/chat`、`/chat/stream`（SSE）、`/chat/sessions` 等，详见 `python/README.md`（部分内容已过时，见下）。

前端：`App.vue` + `components/AudioRecorder.vue`，录音上传走后端 `/upload/asr`。

## 关键约束与已知坑

1. **BOM 不可删**：`spring-ai-alibaba-bom` 不管理 spring-ai 核心构件，`pom.xml` 中的 `spring-ai-bom` 必须保留。
2. **同进程 MCP Client/Server 共存**：`application.yml` 的 `spring.ai.mcp.server.tool-callback-converter` 必须为 `false`，否则 syncTools Bean 触发 `McpSyncClient.initialize()` 导致启动超时。
3. **异步上传**：`/upload/asr` 在丢进线程池前必须先在请求线程 `file.getBytes()`，否则 Tomcat 临时文件已被清理。
4. **端口冲突**：Python 服务 `config.py` 默认 `PORT=8080`，与 Java 后端冲突，启动前用 `.env` 改端口。`python/README.md` 过时：`run.py`、`.env.example` 不存在，所述 8000 端口与实际默认值不符。
5. **SSE 中文**：流式接口须 `produces = "text/event-stream;charset=UTF-8"`，配合 `SseEncodingFilter`。
6. **凭证**：`application.yml` 与 `python/app/config.py` 含本地明文默认凭证，勿外传、勿写入公开交付物。
7. **Git 暂存**：`.gitignore` 未覆盖 `frontend/node_modules/`、`.venv/`、`python/__pycache__/`。禁止 `git add .` / `git add -A`，一律按文件名精确暂存。
8. **资源打包**：`pom.xml` 已将 `src/main/java` 下的 `**/*.xml` 纳入资源（mapper XML 与接口同目录）；MyBatis-Plus `mapper-locations: classpath*:/mapper/**/*.xml`。