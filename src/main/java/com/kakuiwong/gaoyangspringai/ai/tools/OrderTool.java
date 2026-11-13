package com.kakuiwong.gaoyangspringai.ai.tools;

import com.kakuiwong.gaoyangspringai.entity.rag.OrderItem;
import com.kakuiwong.gaoyangspringai.entity.rag.ProductOrder;
import com.kakuiwong.gaoyangspringai.mapper.rag.ProductOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author: gaoyang
 * @Description: 创建订单工具，调用前由 HumanInTheLoopHook 拦截进入人工审批
 */
@Slf4j
@Component
public class OrderTool {

    /** 与 HumanInTheLoopHook 的 approvalOn 保持一致 */
    public static final String CREATE_ORDER_TOOL = "createOrder";

    private static final String STATUS_COMPLETED = "COMPLETED";

    private final ProductOrderMapper productOrderMapper;

    public OrderTool(ProductOrderMapper productOrderMapper) {
        this.productOrderMapper = productOrderMapper;
    }

    @Tool(name = CREATE_ORDER_TOOL, description = "创建订单，将用户确认购买的多件商品写入订单（创建即完成）。"
            + "仅在用户明确确认购买、且商品与数量都明确后调用；调用后会进入人工审核")
    public String createOrder(@ToolParam(description = "用户确认购买的商品列表，每项包含 productId、name、price、quantity")
                              List<OrderItem> products) {
        System.out.println("调用工具createOrder");
        if (CollectionUtils.isEmpty(products)) {
            return "创建订单失败：商品列表为空，请向用户确认要购买的商品。";
        }
        ProductOrder order = new ProductOrder();
        order.setUserId(ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L));
        order.setProducts(products);
        order.setCreateTime(LocalDateTime.now());
        order.setStatus(STATUS_COMPLETED);
        productOrderMapper.insert(order);

        int itemCount = products.stream()
                .map(OrderItem::getQuantity)
                .filter(quantity -> quantity != null && quantity > 0)
                .mapToInt(Integer::intValue)
                .sum();
        log.info("订单创建成功, orderId={}, userId={}, itemCount={}", order.getId(), order.getUserId(), itemCount);
        return String.format("订单创建成功，订单号：%d，用户ID：%d，商品共 %d 件，订单状态：已完成。",
                order.getId(), order.getUserId(), itemCount);
    }
}
