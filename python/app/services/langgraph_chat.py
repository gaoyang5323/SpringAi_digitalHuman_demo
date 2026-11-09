"""
LangGraph 聊天工作流
实现智能对话流程编排，支持 PostgreSQL 上下文记忆
"""
from typing import TypedDict, Annotated, AsyncGenerator, Optional
from langgraph.graph import StateGraph, END, START
from langgraph.graph.message import add_messages
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage
import json

from app.services.langchain_ollama_service import llm_service
from app.services.chat_history_service import chat_history


class ChatState(TypedDict):
    """聊天状态"""
    messages: Annotated[list[BaseMessage], add_messages]
    session_id: Optional[str]
    temperature: float
    max_tokens: int
    # 用于控制是否保存历史
    save_history: bool


class ChatGraph:
    """带记忆的聊天工作流图"""
    
    def __init__(self):
        self.graph = self._build_graph()
    
    def _build_graph(self) -> StateGraph:
        """构建工作流图"""
        workflow = StateGraph(ChatState)
        
        # 添加节点
        workflow.add_node("load_history", self._load_history_node)
        workflow.add_node("chat", self._chat_node)
        workflow.add_node("save_history", self._save_history_node)
        
        # 添加边
        workflow.add_edge(START, "load_history")
        workflow.add_edge("load_history", "chat")
        workflow.add_edge("chat", "save_history")
        workflow.add_edge("save_history", END)
        
        return workflow.compile()
    
    async def _load_history_node(self, state: ChatState) -> dict:
        """加载对话历史节点"""
        session_id = state.get("session_id")
        
        if session_id:
            # 从数据库加载历史消息
            history_messages = await chat_history.get_history_as_langchain_messages(
                session_id,
                limit=20  # 最近20条消息作为上下文
            )
            
            # 合并历史消息和当前消息
            current_messages = state.get("messages", [])
            all_messages = history_messages + current_messages
            
            return {"messages": all_messages}
        
        return {"messages": state.get("messages", [])}
    
    async def _chat_node(self, state: ChatState) -> dict:
        """聊天节点"""
        messages = state.get("messages", [])
        temperature = state.get("temperature", 0.7)
        max_tokens = state.get("max_tokens", 1024)
        
        # 转换为 dict 格式
        messages_dict = [
            {"role": self._get_role(msg), "content": msg.content}
            for msg in messages
        ]
        
        # 调用 LLM
        response = await llm_service.chat(
            messages=messages_dict,
            temperature=temperature,
            num_predict=max_tokens
        )
        
        # 添加 AI 回复到状态
        ai_message = AIMessage(content=response["message"]["content"])
        
        return {
            "messages": [ai_message],
            "save_history": True
        }
    
    async def _save_history_node(self, state: ChatState) -> dict:
        """保存对话历史节点"""
        session_id = state.get("session_id")
        save_history = state.get("save_history", True)
        
        if not save_history:
            return {}
        
        # 确保会话存在
        if session_id:
            await chat_history.get_or_create_session(session_id)
        else:
            session_id = await chat_history.get_or_create_session()
        
        # 保存所有新消息到数据库
        messages = state.get("messages", [])
        
        # 只保存最后两条（用户消息和AI回复）
        if len(messages) >= 2:
            # 保存用户消息
            user_msg = messages[-2]
            if isinstance(user_msg, HumanMessage):
                await chat_history.save_message(
                    session_id=session_id,
                    role="user",
                    content=user_msg.content
                )
            
            # 保存AI回复
            ai_msg = messages[-1]
            if isinstance(ai_msg, AIMessage):
                await chat_history.save_message(
                    session_id=session_id,
                    role="assistant",
                    content=ai_msg.content,
                    model="Qwen2.5:7b"
                )
        
        return {"session_id": session_id}
    
    def _get_role(self, message: BaseMessage) -> str:
        """获取消息角色"""
        if isinstance(message, HumanMessage):
            return "user"
        elif isinstance(message, AIMessage):
            return "assistant"
        else:
            return "system"
    
    async def invoke(self, state: ChatState) -> ChatState:
        """执行工作流"""
        result = await self.graph.ainvoke(state)
        return result
    
    async def stream(self, state: ChatState) -> AsyncGenerator[dict, None]:
        """流式执行工作流"""
        async for event in self.graph.astream(state):
            yield event


class StreamChatGraph:
    """流式聊天工作流图（带记忆）"""
    
    def __init__(self):
        self.graph = self._build_graph()
    
    def _build_graph(self) -> StateGraph:
        """构建工作流图"""
        workflow = StateGraph(ChatState)
        
        # 添加节点
        workflow.add_node("load_history", self._load_history_node)
        workflow.add_node("stream_chat", self._stream_chat_node)
        workflow.add_node("save_history", self._save_history_node)
        
        # 添加边
        workflow.add_edge(START, "load_history")
        workflow.add_edge("load_history", "stream_chat")
        workflow.add_edge("stream_chat", "save_history")
        workflow.add_edge("save_history", END)
        
        return workflow.compile()
    
    async def _load_history_node(self, state: ChatState) -> dict:
        """加载对话历史节点"""
        session_id = state.get("session_id")
        
        if session_id:
            history_messages = await chat_history.get_history_as_langchain_messages(
                session_id,
                limit=20
            )
            current_messages = state.get("messages", [])
            all_messages = history_messages + current_messages
            return {"messages": all_messages}
        
        return {"messages": state.get("messages", [])}
    
    async def _stream_chat_node(self, state: ChatState) -> dict:
        """流式聊天节点"""
        messages = state.get("messages", [])
        temperature = state.get("temperature", 0.7)
        max_tokens = state.get("max_tokens", 1024)
        
        messages_dict = [
            {"role": self._get_role(msg), "content": msg.content}
            for msg in messages
        ]
        
        collected_messages = []
        async for chunk in llm_service.chat_stream(
            messages=messages_dict,
            temperature=temperature,
            num_predict=max_tokens
        ):
            content = chunk["message"]["content"]
            if content:
                collected_messages.append(content)
        
        full_content = "".join(collected_messages)
        ai_message = AIMessage(content=full_content)
        
        return {
            "messages": [ai_message],
            "save_history": True
        }
    
    async def _save_history_node(self, state: ChatState) -> dict:
        """保存对话历史节点"""
        session_id = state.get("session_id")
        save_history = state.get("save_history", True)
        
        if not save_history:
            return {}
        
        if session_id:
            await chat_history.get_or_create_session(session_id)
        else:
            session_id = await chat_history.get_or_create_session()
        
        messages = state.get("messages", [])
        
        if len(messages) >= 2:
            user_msg = messages[-2]
            if isinstance(user_msg, HumanMessage):
                await chat_history.save_message(
                    session_id=session_id,
                    role="user",
                    content=user_msg.content
                )
            
            ai_msg = messages[-1]
            if isinstance(ai_msg, AIMessage):
                await chat_history.save_message(
                    session_id=session_id,
                    role="assistant",
                    content=ai_msg.content,
                    model="Qwen2.5:7b"
                )
        
        return {"session_id": session_id}
    
    def _get_role(self, message: BaseMessage) -> str:
        """获取消息角色"""
        if isinstance(message, HumanMessage):
            return "user"
        elif isinstance(message, AIMessage):
            return "assistant"
        else:
            return "system"
    
    async def stream(self, state: ChatState) -> AsyncGenerator[dict, None]:
        """流式执行工作流"""
        async for event in self.graph.astream(state):
            yield event


# 全局图实例
chat_graph = ChatGraph()
stream_chat_graph = StreamChatGraph()
