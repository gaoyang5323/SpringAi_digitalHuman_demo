package com.kakuiwong.gaoyangspringai.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: gaoyang
 * @Description: Excel 上传向量化结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorUploadResult {

    /** 血缘id：一次上传批次一个，可追溯到源文件与行号 */
    private String lineageId;

    private String fileName;

    /** 成功插入的向量条数 */
    private int inserted;

    /** 跳过的空行数 */
    private int skipped;
}