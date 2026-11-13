package com.kakuiwong.gaoyangspringai.controller.rag;

import com.kakuiwong.gaoyangspringai.common.Result;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderApprovalRequest;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderChatRequest;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderChatResult;
import com.kakuiwong.gaoyangspringai.service.rag.OrderChatService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: gaoyang
 * @Description: 订单导购对话接口：RAG 商品检索 + 创建订单人工审批
 */
@RestController
@RequestMapping("/rag/order")
public class OrderChatController {

    private final OrderChatService orderChatService;

    public OrderChatController(OrderChatService orderChatService) {
        this.orderChatService = orderChatService;
    }

    /**
     * 对话：msg 与 sessionId 必传；sessionId 相同的多轮对话共享上下文。
     * 返回 WAITING_APPROVAL 时，需携带 pending.toolCallId 调用 /approval 完成审批。
     */
    @PostMapping("/chat")
    public Result<OrderChatResult> chat(@RequestBody OrderChatRequest request) {
        if (request == null || !StringUtils.hasText(request.msg()) || !StringUtils.hasText(request.sessionId())) {
            return Result.fail("msg 和 sessionId 不能为空");
        }
        try {
            return Result.ok(orderChatService.chat(request));
        } catch (Exception e) {
            return Result.fail("订单对话失败: " + e.getMessage());
        }
    }

    /**
     * 人工审批创建订单：APPROVE 原样下单；REJECT 拒绝并说明原因；
     * EDIT 用 products 覆盖商品与数量后下单。
     */
    @PostMapping("/approval")
    public Result<OrderChatResult> approval(@RequestBody OrderApprovalRequest request) {
        if (request == null || !StringUtils.hasText(request.sessionId()) || request.decision() == null) {
            return Result.fail("sessionId、decision 不能为空");
        }
        try {
            return Result.ok(orderChatService.approval(request));
        } catch (Exception e) {
            return Result.fail("订单审批失败: " + e.getMessage());
        }
    }
}
