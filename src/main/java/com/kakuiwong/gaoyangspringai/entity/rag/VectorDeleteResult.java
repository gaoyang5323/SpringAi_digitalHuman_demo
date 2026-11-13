package com.kakuiwong.gaoyangspringai.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: gaoyang
 * @Description: 向量删除结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorDeleteResult {

    /** 实际删除的向量条数 */
    private int deletedCount;
}