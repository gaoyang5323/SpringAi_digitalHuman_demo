package com.kakuiwong.gaoyangspringai.entity.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.Map;

/**
 * @author: gaoyang
 * @Description: Spring AI PgVectorStore 默认向量表 vector_store 的管理视图。
 * 不映射 embedding 列：管理接口不需要向量本体，且 pgvector 类型无默认 MyBatis 处理器。
 */
@Data
@TableName(value = "vector_store", autoResultMap = true)
public class VectorRecord {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 原文内容（Excel 行原始字符串） */
    private String content;

    /** 元数据：lineageId / fileName / sheetName / rowIndex / createdAt */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
}