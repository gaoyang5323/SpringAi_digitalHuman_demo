package com.kakuiwong.gaoyangspringai.entity.rag;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/**
 * @author: gaoyang
 * @Description: 分页结果业务对象
 */
@Data
public class PageResult<T> {

    private long total;

    private long pageNum;

    private long pageSize;

    private List<T> records;

    public static <T> PageResult<T> from(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.total = page.getTotal();
        result.pageNum = page.getCurrent();
        result.pageSize = page.getSize();
        result.records = page.getRecords();
        return result;
    }
}