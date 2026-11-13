package com.kakuiwong.gaoyangspringai.entity.rag;

import java.util.List;

/**
 * @author: gaoyang
 * @Description: 创建订单人工审批请求：批准 / 拒绝 / 修改数量
 */
public record OrderApprovalRequest(String sessionId,
                                   String toolCallId,
                                   Decision decision,
                                   List<OrderItem> products,
                                   String reason) {

    public enum Decision {
        /** 批准，按原商品与数量下单 */
        APPROVE,
        /** 拒绝，不创建订单 */
        REJECT,
        /** 修改商品或数量后下单，products 必传 */
        EDIT
    }
}
