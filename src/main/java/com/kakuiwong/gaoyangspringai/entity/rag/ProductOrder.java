package com.kakuiwong.gaoyangspringai.entity.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author: gaoyang
 * @Description: 商品订单表
 */
@Data
@TableName(value = "product_order", autoResultMap = true)
public class ProductOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 随机用户id */
    private Long userId;

    /** 商品 JSON 数组 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<OrderItem> products;

    private LocalDateTime createTime;

    /** 订单状态，创建即完成 */
    private String status;
}
