import httpx
import json
from typing import AsyncGenerator, Generator
from app.config import settings


class OllamaService:
    """Ollama 大模型服务"""
    
    def __init__(self):
        self.base_url = settings.OLLAMA_BASE_URL
        self.model = settings.OLLAMA_MODEL
        self.temperature = settings.OLLAMA_TEMPERATURE
        self.num_predict = settings.OLLAMA_NUM_PREDICT
    
    async def chat(
        self,
        messages: list[dict],
        stream: bool = False,
        **kwargs
    ) -> dict | AsyncGenerator[dict, None]:
        """
        与 Ollama 模型对话
        
        Args:
            messages: 消息列表，格式: [{"role": "user", "content": "..."}]
            stream: 是否流式返回
            **kwargs: 其他参数
            
        Returns:
            非流式: 返回完整响应 dict
            流式: 返回异步生成器
        """
        payload = {
            "model": self.model,
            "messages": messages,
            "stream": stream,
            "options": {
                "temperature": kwargs.get("temperature", self.temperature),
                "num_predict": kwargs.get("num_predict", self.num_predict),
            }
        }
        
        if stream:
            return self._stream_chat(payload)
        else:
            async with httpx.AsyncClient(timeout=httpx.Timeout(300.0)) as client:
                response = await client.post(
                    f"{self.base_url}/api/chat",
                    json=payload
                )
                response.raise_for_status()
                return response.json()
    
    async def _stream_chat(
        self,
        payload: dict
    ) -> AsyncGenerator[dict, None]:
        """流式聊天"""
        async with httpx.AsyncClient(timeout=httpx.Timeout(300.0)) as client:  
            async with client.stream(
                "POST",
                f"{self.base_url}/api/chat",
                json=payload
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if line:
                        try:
                            data = json.loads(line)
                            yield data
                        except json.JSONDecodeError:
                            continue
    
    async def generate(
        self,
        prompt: str,
        stream: bool = False,
        **kwargs
    ) -> dict | AsyncGenerator[dict, None]:
        """
        使用 Ollama 生成文本
        
        Args:
            prompt: 提示词
            stream: 是否流式返回
            
        Returns:
            生成结果
        """
        payload = {
            "model": self.model,
            "prompt": prompt,
            "stream": stream,
            "options": {
                "temperature": kwargs.get("temperature", self.temperature),
                "num_predict": kwargs.get("num_predict", self.num_predict),
            }
        }
        
        if stream:
            return self._stream_generate(payload)
        else:
            async with httpx.AsyncClient(timeout=httpx.Timeout(300.0)) as client:
                response = await client.post(
                    f"{self.base_url}/api/generate",
                    json=payload
                )
                response.raise_for_status()
                return response.json()
    
    async def _stream_generate(
        self,
        payload: dict
    ) -> AsyncGenerator[dict, None]:
        """流式生成"""
        async with httpx.AsyncClient(timeout=httpx.Timeout(300.0)) as client:
            async with client.stream(
                "POST",
                f"{self.base_url}/api/generate",
                json=payload
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if line:
                        try:
                            data = json.loads(line)
                            yield data
                        except json.JSONDecodeError:
                            continue
    
    async def is_available(self) -> bool:
        """检查 Ollama 服务是否可用"""
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(f"{self.base_url}/api/tags")
                return response.status_code == 200
        except Exception:
            return False
    
    async def list_models(self) -> list[dict]:
        """列出可用的模型"""
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(f"{self.base_url}/api/tags")
                response.raise_for_status()
                data = response.json()
                return data.get("models", [])
        except Exception as e:
            return []


# 创建全局服务实例
ollama_service = OllamaService()
