"""
LangChain Ollama 服务
提供大模型调用能力
"""
from typing import AsyncGenerator, Optional
from langchain_ollama import ChatOllama
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage, BaseMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from app.config import settings


class LangChainOllamaService:
    """基于 LangChain 的 Ollama 服务"""
    
    def __init__(self):
        self.llm = ChatOllama(
            model=settings.OLLAMA_MODEL,
            temperature=settings.OLLAMA_TEMPERATURE,
            num_predict=settings.OLLAMA_NUM_PREDICT,
            base_url=settings.OLLAMA_BASE_URL,
        )
        
        # 创建聊天提示模板
        self.prompt = ChatPromptTemplate.from_messages([
            ("system", "你是一个有用的AI助手。"),
            MessagesPlaceholder(variable_name="messages"),
        ])
    
    def _convert_messages(self, messages: list[dict]) -> list[BaseMessage]:
        """转换消息格式为 LangChain 格式"""
        result = []
        for msg in messages:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            
            if role == "system":
                result.append(SystemMessage(content=content))
            elif role == "assistant":
                result.append(AIMessage(content=content))
            else:
                result.append(HumanMessage(content=content))
        return result
    
    async def chat(
        self,
        messages: list[dict],
        **kwargs
    ) -> dict:
        """
        同步聊天
        
        Args:
            messages: 消息列表
            **kwargs: 其他参数
            
        Returns:
            响应字典
        """
        langchain_messages = self._convert_messages(messages)
        
        # 调用 LLM
        response = await self.llm.ainvoke(langchain_messages)
        
        return {
            "message": {
                "role": "assistant",
                "content": response.content,
            },
            "model": settings.OLLAMA_MODEL,
            "done_reason": "stop",
            "total_tokens": response.usage_metadata.get("total_tokens", 0) if response.usage_metadata else 0,
        }
    
    async def chat_stream(
        self,
        messages: list[dict],
        **kwargs
    ) -> AsyncGenerator[dict, None]:
        """
        流式聊天
        
        Args:
            messages: 消息列表
            **kwargs: 其他参数
            
        Yields:
            响应块
        """
        langchain_messages = self._convert_messages(messages)
        
        full_content = ""
        async for chunk in self.llm.astream(langchain_messages):
            content = chunk.content
            if content:
                full_content += content
                yield {
                    "message": {
                        "role": "assistant",
                        "content": content,
                    },
                    "model": settings.OLLAMA_MODEL,
                    "done": False,
                }
        
        # 发送结束标记
        yield {
            "message": {
                "role": "assistant",
                "content": "",
            },
            "model": settings.OLLAMA_MODEL,
            "done": True,
            "done_reason": "stop",
        }
    
    async def is_available(self) -> bool:
        """检查 Ollama 服务是否可用"""
        try:
            import httpx
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(f"{settings.OLLAMA_BASE_URL}/api/tags")
                return response.status_code == 200
        except Exception:
            return False
    
    async def list_models(self) -> list[dict]:
        """列出可用模型"""
        try:
            import httpx
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(f"{settings.OLLAMA_BASE_URL}/api/tags")
                response.raise_for_status()
                data = response.json()
                return data.get("models", [])
        except Exception:
            return []


# 全局服务实例
llm_service = LangChainOllamaService()
