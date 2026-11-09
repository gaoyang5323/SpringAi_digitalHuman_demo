from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.config import settings
from app.database import init_db, close_db
from app.routers import chat


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时初始化数据库
    await init_db()
    print("✅ 数据库连接已初始化")
    
    yield
    
    # 关闭时清理数据库连接
    await close_db()
    print("🔌 数据库连接已关闭")


app = FastAPI(
    title="Python Chat API",
    description="支持 Ollama 大模型的聊天 API，支持同步和流式返回",
    version="1.0.0",
    lifespan=lifespan
)

# 配置 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(chat.router)


@app.get("/")
async def root():
    """根路径"""
    return {
        "message": "Python Chat API 已启动",
        "docs": "/docs",
        "health": "/chat/health"
    }


@app.get("/health")
async def health():
    """健康检查"""
    return {"status": "healthy"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=True
    )
