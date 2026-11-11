package com.kakuiwong.gaoyangspringai.ai.tools;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author: gaoyang
 * @Description:
 */
@Component
public class HUMANFEEDBACKTool {

    @Tool(name = "refund", description = "执行退款操作，需要人工审批后执行")
    public String refund(@ToolParam(description = "退款订单号") String orderId) {
        System.out.println("HUMANFEEDBACKTool->refund, orderId=" + orderId);
        return "退款成功，订单号：" + orderId;
    }
}

