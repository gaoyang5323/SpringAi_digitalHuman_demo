package com.kakuiwong.gaoyangspringai.mapper.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author: gaoyang
 * @Description: 向量数据管理 Mapper（vector_store 表）
 */
public interface VectorRecordMapper extends BaseMapper<VectorRecord> {

    /**
     * 按 id 批量删除。不用 BaseMapper.deleteByIds：pgjdbc 默认把 setString 绑为 varchar，
     * uuid = varchar 无操作符会报错，显式 CAST 保留主键索引。
     */
    @Delete("<script>DELETE FROM vector_store WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>CAST(#{id} AS uuid)</foreach>" +
            "</script>")
    int deleteByStringIds(@Param("ids") List<String> ids);
}