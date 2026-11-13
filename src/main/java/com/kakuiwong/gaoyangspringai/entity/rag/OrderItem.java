package com.kakuiwong.gaoyangspringai.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author: gaoyang
 * @Description: 订单商品项，作为商品 JSON 数组中的元素
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private String productId;

    private String name;

    private BigDecimal price;

    private Integer quantity;
}
