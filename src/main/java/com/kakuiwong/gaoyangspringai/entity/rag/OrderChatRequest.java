package com.kakuiwong.gaoyangspringai.entity.rag;

/**
 * @author: gaoyang
 * @Description: 订单导购对话请求，msg 与 sessionId 均必传
 */
public record OrderChatRequest(String msg, String sessionId) {
}
