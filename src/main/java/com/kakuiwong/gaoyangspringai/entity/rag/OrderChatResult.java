package com.kakuiwong.gaoyangspringai.entity.rag;

/**
 * @author: gaoyang
 * @Description: 订单导购对话结果；WAITING_APPROVAL 时携带待人工审批的订单工具调用
 */
public record OrderChatResult(Status status, String answer, PendingApproval pending) {

    public enum Status {
        /** 本轮对话结束 */
        COMPLETED,
        /** 等待人工审批创建订单 */
        WAITING_APPROVAL
    }

    public record PendingApproval(String toolCallId, String toolName, String arguments, String description) {
    }

    public static OrderChatResult completed(String answer) {
        return new OrderChatResult(Status.COMPLETED, answer, null);
    }

    public static OrderChatResult waitingApproval(String answer, PendingApproval pending) {
        return new OrderChatResult(Status.WAITING_APPROVAL, answer, pending);
    }
}
