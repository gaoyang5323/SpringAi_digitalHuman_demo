"""
对话历史服务
管理 PostgreSQL 中的对话上下文
"""
from typing import Optional, AsyncGenerator
from datetime import datetime
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, BaseMessage

from app.database import async_session_factory
from app.models import ChatMessage, ChatSession


class ChatHistoryService:
    """对话历史服务"""
    
    @staticmethod
    async def get_or_create_session(session_id: Optional[str] = None) -> str:
        """获取或创建会话"""
        import uuid
        
        async with async_session_factory() as session:
            if session_id:
                # 查找现有会话
                stmt = select(ChatSession).where(ChatSession.session_id == session_id)
                result = await session.execute(stmt)
                chat_session = result.scalar_one_or_none()
                
                if chat_session:
                    return chat_session.session_id
            
            # 创建新会话
            new_session_id = session_id or str(uuid.uuid4())
            new_session = ChatSession(
                session_id=new_session_id,
                title=f"对话 {datetime.now().strftime('%Y-%m-%d %H:%M')}"
            )
            session.add(new_session)
            await session.commit()
            return new_session_id
    
    @staticmethod
    async def save_message(
        session_id: str,
        role: str,
        content: str,
        model: Optional[str] = None,
        metadata: Optional[dict] = None
    ) -> ChatMessage:
        """保存消息到数据库"""
        async with async_session_factory() as session:
            message = ChatMessage(
                session_id=session_id,
                role=role,
                content=content,
                model=model,
                metadata_=metadata
            )
            session.add(message)
            await session.commit()
            await session.refresh(message)
            return message
    
    @staticmethod
    async def get_history(
        session_id: str,
        limit: Optional[int] = None
    ) -> list[ChatMessage]:
        """获取对话历史"""
        async with async_session_factory() as session:
            stmt = (
                select(ChatMessage)
                .where(ChatMessage.session_id == session_id)
                .order_by(ChatMessage.created_at.asc())
            )
            
            if limit:
                stmt = stmt.limit(limit)
            
            result = await session.execute(stmt)
            messages = result.scalars().all()
            return list(messages)
    
    @staticmethod
    async def get_history_as_langchain_messages(
        session_id: str,
        limit: Optional[int] = None
    ) -> list[BaseMessage]:
        """获取对话历史并转换为 LangChain 消息格式"""
        db_messages = await ChatHistoryService.get_history(session_id, limit)
        
        langchain_messages = []
        for msg in db_messages:
            if msg.role == "user":
                langchain_messages.append(HumanMessage(content=msg.content))
            elif msg.role == "assistant":
                langchain_messages.append(AIMessage(content=msg.content))
            elif msg.role == "system":
                langchain_messages.append(SystemMessage(content=msg.content))
        
        return langchain_messages
    
    @staticmethod
    async def clear_history(session_id: str) -> int:
        """清空对话历史"""
        async with async_session_factory() as session:
            stmt = delete(ChatMessage).where(ChatMessage.session_id == session_id)
            result = await session.execute(stmt)
            await session.commit()
            return result.rowcount
    
    @staticmethod
    async def delete_session(session_id: str) -> bool:
        """删除会话及其所有消息"""
        async with async_session_factory() as session:
            # 先删除消息
            stmt = delete(ChatMessage).where(ChatMessage.session_id == session_id)
            await session.execute(stmt)
            
            # 再删除会话
            stmt = delete(ChatSession).where(ChatSession.session_id == session_id)
            result = await session.execute(stmt)
            
            await session.commit()
            return result.rowcount > 0
    
    @staticmethod
    async def list_sessions(
        limit: int = 50,
        offset: int = 0
    ) -> list[ChatSession]:
        """列出所有会话"""
        async with async_session_factory() as session:
            stmt = (
                select(ChatSession)
                .order_by(ChatSession.updated_at.desc())
                .limit(limit)
                .offset(offset)
            )
            result = await session.execute(stmt)
            return list(result.scalars().all())
    
    @staticmethod
    async def update_session_title(session_id: str, title: str) -> bool:
        """更新会话标题"""
        async with async_session_factory() as session:
            stmt = select(ChatSession).where(ChatSession.session_id == session_id)
            result = await session.execute(stmt)
            chat_session = result.scalar_one_or_none()
            
            if chat_session:
                chat_session.title = title
                await session.commit()
                return True
            return False


# 全局服务实例
chat_history = ChatHistoryService()
