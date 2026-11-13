package com.kakuiwong.gaoyangspringai.controller.rag;

import com.kakuiwong.gaoyangspringai.common.Result;
import com.kakuiwong.gaoyangspringai.entity.rag.PageResult;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorDeleteResult;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorRecord;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorUploadResult;
import com.kakuiwong.gaoyangspringai.service.rag.VectorManageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author: gaoyang
 * @Description: RAG 向量数据管理接口：Excel 上传向量化、分页筛选、删除
 */
@RestController
@RequestMapping("/rag/vectors")
public class VectorManageController {

    private final VectorManageService vectorManageService;

    public VectorManageController(VectorManageService vectorManageService) {
        this.vectorManageService = vectorManageService;
    }

    /**
     * 上传 Excel 批量插入向量。
     * file: .xlsx/.xls，首行表头；lineageId 可选（默认生成批次 UUID）；contentColumn 可选（默认整行拼接）
     */
    @PostMapping("/upload")
    public Result<VectorUploadResult> upload(@RequestPart("file") MultipartFile file,
                                             @RequestParam(value = "lineageId", required = false) String lineageId,
                                             @RequestParam(value = "contentColumn", required = false) String contentColumn) {
        try {
            return Result.ok(vectorManageService.uploadExcel(file, lineageId, contentColumn));
        } catch (Exception e) {
            return Result.fail("Excel 向量插入失败: " + e.getMessage());
        }
    }

    /** 分页列表：lineageId / fileName 精确筛选，keyword 原文模糊筛选，sort=asc/desc 按入库时间排序 */
    @GetMapping("/page")
    public Result<PageResult<VectorRecord>> page(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                                 @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                                 @RequestParam(value = "lineageId", required = false) String lineageId,
                                                 @RequestParam(value = "fileName", required = false) String fileName,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "sort", defaultValue = "desc") String sort) {
        try {
            return Result.ok(vectorManageService.page(pageNum, pageSize, lineageId, fileName, keyword, sort));
        } catch (Exception e) {
            return Result.fail("查询向量列表失败: " + e.getMessage());
        }
    }

    /** 按向量 id 批量删除，body: ["id1","id2"] */
    @DeleteMapping
    public Result<VectorDeleteResult> deleteByIds(@RequestBody List<String> ids) {
        try {
            return Result.ok(vectorManageService.deleteByIds(ids));
        } catch (Exception e) {
            return Result.fail("删除向量失败: " + e.getMessage());
        }
    }

    /** 按血缘id整批删除 */
    @DeleteMapping("/lineage/{lineageId}")
    public Result<VectorDeleteResult> deleteByLineage(@PathVariable("lineageId") String lineageId) {
        try {
            return Result.ok(vectorManageService.deleteByLineageId(lineageId));
        } catch (Exception e) {
            return Result.fail("按血缘删除失败: " + e.getMessage());
        }
    }
}