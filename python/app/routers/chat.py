"""
聊天路由
提供 /chat 接口，支持会话上下文
"""
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel
from typing import Optional, AsyncGenerator
from sse_starlette.sse import EventSourceResponse
from langchain_core.messages import HumanMessage
import json

from app.services.langgraph_chat import chat_graph, stream_chat_graph
from app.services.langchain_ollama_service import llm_service
from app.services.chat_history_service import chat_history
from app.config import settings

router = APIRouter(prefix="/chat", tags=["聊天"])


class ChatResponse(BaseModel):
    role: str
    content: str
    model: str
    finish_reason: Optional[str] = None
    total_tokens: Optional[int] = 0
    session_id: Optional[str] = None


class SessionResponse(BaseModel):
    session_id: str
    title: Optional[str] = None
    created_at: Optional[str] = None
    updated_at: Optional[str] = None


class MessageResponse(BaseModel):
    id: int
    role: str
    content: str
    model: Optional[str] = None
    created_at: Optional[str] = None


@router.get("", response_model=ChatResponse)
async def chat(
    message: str = Query(..., description="用户消息"),
    temperature: float = Query(0.7, description="温度参数"),
    max_tokens: int = Query(1024, description="最大token数"),
    session_id: Optional[str] = Query(None, description="会话ID，用于延续对话上下文")
):
    """
    同步聊天接口
    
    使用 LangGraph 工作流处理对话，支持 PostgreSQL 上下文记忆
    
    - 传入 session_id 可延续历史对话上下文
    - 不传 session_id 则创建新会话
    """
    try:
        # 获取或创建会话
        actual_session_id = await chat_history.get_or_create_session(session_id)
        
        # 构建初始状态
        initial_state = {
            "messages": [HumanMessage(content=message)],
            "session_id": actual_session_id,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "save_history": True,
        }
        
        # 执行工作流
        result = await chat_graph.invoke(initial_state)
        
        # 获取 AI 回复
        ai_messages = [msg for msg in result["messages"] if hasattr(msg, 'content') and msg.type == 'ai']
        if ai_messages:
            content = ai_messages[-1].content
        else:
            content = ""
        
        return ChatResponse(
            role="assistant",
            content=content,
            model=settings.OLLAMA_MODEL,
            finish_reason="stop",
            total_tokens=result.get("total_tokens", 0),
            session_id=actual_session_id
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
        # 获取或创建会话
        actual_session_id = await chat_history.get_or_create_session(session_id)
        
        async def event_generator() -> AsyncGenerator[dict, None]:
            # 先发送 session_id
            yield {
                "event": "session",
                "data": json.dumps({"session_id": actual_session_id})
            }
            
            # 获取历史上下文
            history_messages = await chat_history.get_history_as_langchain_messages(
                actual_session_id,
                limit=20
            )
            
            # 添加当前用户消息
            history_messages.append(HumanMessage(content=message))
            
            # 转换为 dict 格式
            messages_dict = [
                {"role": "user" if msg.type == "human" else "assistant", "content": msg.content}
                for msg in history_messages
            ]
            
            full_content = ""
            async for chunk in llm_service.chat_stream(
                messages=messages_dict,
                temperature=temperature,
                num_predict=max_tokens
            ):
                if chunk.get("done", False):
                    yield {
                        "event": "done",
                        "data": json.dumps({
                            "model": chunk.get("model", ""),
                            "finish_reason": chunk.get("done_reason", "stop"),
                            "session_id": actual_session_id
                        })
                    }
                else:
                    msg = chunk.get("message", {})
                    content = msg.get("content", "")
                    if content:
                        full_content += content
                        yield {
                            "event": "message",
                            "data": json.dumps({
                                "role": msg.get("role", "assistant"),
                                "content": content,
                                "model": chunk.get("model", "")
                            })
                        }
            
            # 保存消息到数据库
            await chat_history.save_message(
                session_id=actual_session_id,
                role="user",
                content=message
            )
            await chat_history.save_message(
                session_id=actual_session_id,
                role="assistant",
                content=full_content,
                model=settings.OLLAMA_MODEL
            )
        
        return EventSourceResponse(event_generator())
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 会话管理接口 ====================

@router.get("/sessions", response_model=list[SessionResponse])
async def list_sessions(
    limit: int = Query(50, description="返回数量"),
    offset: int = Query(0, description="偏移量")
):
    """获取会话列表"""
    try:
        sessions = await chat_history.list_sessions(limit, offset)
        return [
            SessionResponse(
                session_id=s.session_id,
                title=s.title,
                created_at=s.created_at.isoformat() if s.created_at else None,
                updated_at=s.updated_at.isoformat() if s.updated_at else None
            )
            for s in sessions
        ]
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/sessions/{session_id}/history", response_model=list[MessageResponse])
async def get_session_history(
    session_id: str,
    limit: Optional[int] = Query(None, description="返回消息数量")
):
    """获取指定会话的历史消息"""
    try:
        messages = await chat_history.get_history(session_id, limit)
        return [
            MessageResponse(
                id=m.id,
                role=m.role,
                content=m.content,
                model=m.model,
                created_at=m.created_at.isoformat() if m.created_at else None
            )
            for m in messages
        ]
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/sessions/{session_id}")
async def delete_session(session_id: str):
    """删除会话及其历史消息"""
    try:
        success = await chat_history.delete_session(session_id)
        if success:
            return {"message": f"会话 {session_id} 已删除"}
        raise HTTPException(status_code=404, detail="会话不存在")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/sessions/{session_id}/history")
async def clear_session_history(session_id: str):
    """清空会话历史但保留会话"""
    try:
        count = await chat_history.clear_history(session_id)
        return {"message": f"已清空 {count} 条消息"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.put("/sessions/{session_id}/title")
async def update_session_title(
    session_id: str,
    title: str = Query(..., description="新标题")
):
    """更新会话标题"""
    try:
        success = await chat_history.update_session_title(session_id, title)
        if success:
            return {"message": "标题已更新"}
        raise HTTPException(status_code=404, detail="会话不存在")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 其他接口 ====================

@router.get("/models")
async def list_models():
    """获取可用的模型列表"""
    try:
        models = await llm_service.list_models()
        return {"models": models}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health_check():
    """健康检查"""
    is_available = await llm_service.is_available()
    return {
        "status": "healthy" if is_available else "unhealthy",
        "ollama_available": is_available
    }
