from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from typing import Optional, AsyncGenerator
from sse_starlette.sse import EventSourceResponse
import json

from app.services.ollama_service import ollama_service
from app.config import settings

router = APIRouter(prefix="/chat", tags=["聊天"])


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    messages: list[ChatMessage]
    stream: bool = False
    temperature: Optional[float] = 0.7
    max_tokens: Optional[int] = 1024
    session_id: Optional[str] = None


class ChatResponse(BaseModel):
    role: str
    content: str
    model: str
    finish_reason: Optional[str] = None


@router.get("", response_model=ChatResponse)
async def chat(
    message: str = Query(..., description="用户消息"),
    temperature: float = Query(0.7, description="温度参数"),
    max_tokens: int = Query(1024, description="最大token数"),
    session_id: Optional[str] = Query(None, description="会话ID")
):
    """
    同步聊天接口
    
    发送消息并获取完整回复
    """
    try:
        messages = [{"role": "user", "content": message}]
        
        response = await ollama_service.chat(
            messages=messages,
            stream=False,
            temperature=temperature,
            num_predict=max_tokens
        )
        
        message_data = response.get("message", {})
        
        return ChatResponse(
            role=message_data.get("role", "assistant"),
            content=message_data.get("content", ""),
            model=response.get("model", settings.OLLAMA_MODEL),
            finish_reason=response.get("done_reason", "stop")
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stream")
async def chat_stream(
    message: str = Query(..., description="用户消息"),
    temperature: float = Query(0.7, description="温度参数"),
    max_tokens: int = Query(1024, description="最大token数"),
    session_id: Optional[str] = Query(None, description="会话ID")
):
    """
    流式聊天接口
    
    使用 Server-Sent Events (SSE) 流式返回回复
    """
    try:
        messages = [{"role": "user", "content": message}]
        
        async def event_generator() -> AsyncGenerator[dict, None]:
            stream_response = await ollama_service.chat(
                messages=messages,
                stream=True,
                temperature=temperature,
                num_predict=max_tokens
            )
            
            async for chunk in stream_response:
                if chunk.get("done", False):
                    # 发送结束事件
                    yield {
                        "event": "done",
                        "data": json.dumps({
                            "model": chunk.get("model", ""),
                            "finish_reason": chunk.get("done_reason", "stop")
                        })
                    }
                else:
                    # 发送内容块
                    msg = chunk.get("message", {})
                    content = msg.get("content", "")
                    if content:
                        yield {
                            "event": "message",
                            "data": json.dumps({
                                "role": msg.get("role", "assistant"),
                                "content": content,
                                "model": chunk.get("model", "")
                            })
                        }
        
        return EventSourceResponse(event_generator())
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/models")
async def list_models():
    """获取可用的模型列表"""
    try:
        models = await ollama_service.list_models()
        return {"models": models}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health_check():
    """健康检查"""
    is_available = await ollama_service.is_available()
    return {
        "status": "healthy" if is_available else "unhealthy",
        "ollama_available": is_available
    }
