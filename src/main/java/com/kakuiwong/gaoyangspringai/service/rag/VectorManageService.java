package com.kakuiwong.gaoyangspringai.service.rag;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kakuiwong.gaoyangspringai.entity.rag.PageResult;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorDeleteResult;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorRecord;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorUploadResult;
import com.kakuiwong.gaoyangspringai.mapper.rag.VectorRecordMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * @author: gaoyang
 * @Description: RAG 向量数据管理：Excel 批量向量化入库（元数据 + 原文 + 血缘id），分页筛选与删除
 */
@Service
public class VectorManageService {

    /** 每批向量化条数，控制单次内存与嵌入调用规模 */
    private static final int BATCH_SIZE = 500;

    /** 单次上传最大数据行数（不含表头） */
    private static final int MAX_UPLOAD_ROWS = 50;

    /** 固定宽度时间格式，保证 metadata->>'createdAt' 文本排序即时间排序 */
    private static final DateTimeFormatter CREATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final VectorStore vectorStore;
    private final VectorRecordMapper vectorRecordMapper;

    public VectorManageService(VectorStore vectorStore, VectorRecordMapper vectorRecordMapper) {
        this.vectorStore = vectorStore;
        this.vectorRecordMapper = vectorRecordMapper;
    }

    /**
     * 上传 Excel 批量插入向量。首行为表头，每个数据行生成一条向量。
     *
     * @param file          Excel 文件（.xlsx/.xls）
     * @param lineageId     血缘id，可选；不传则按本次上传生成一个批次 UUID
     * @param contentColumn 参与向量化的列名（表头），可选；不传则整行拼接 "表头: 值"
     */
    public VectorUploadResult uploadExcel(MultipartFile file, String lineageId, String contentColumn) {
        String fileName = file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        if (fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 .xlsx / .xls 文件");
        }
        String batchLineageId = StringUtils.hasText(lineageId) ? lineageId.trim() : UUID.randomUUID().toString();

        ExcelRowListener listener = new ExcelRowListener(batchLineageId, fileName, contentColumn);
        try {
            EasyExcel.read(file.getInputStream(), listener).headRowNumber(1).doReadAll();
        } catch (IOException e) {
            throw new UncheckedIOException("读取 Excel 失败", e);
        }
        listener.flush();

        return new VectorUploadResult(batchLineageId, fileName, listener.inserted, listener.skipped);
    }

    /** 分页列表：lineageId / fileName 精确筛选，keyword 原文模糊筛选，按入库时间排序（默认倒序） */
    public PageResult<VectorRecord> page(long pageNum, long pageSize, String lineageId,
                                         String fileName, String keyword, String sort) {
        QueryWrapper<VectorRecord> wrapper = new QueryWrapper<>();
        wrapper.apply(StringUtils.hasText(lineageId), "metadata->>'lineageId' = {0}", lineageId);
        wrapper.apply(StringUtils.hasText(fileName), "metadata->>'fileName' = {0}", fileName);
        wrapper.apply(StringUtils.hasText(keyword), "content ILIKE {0}", "%" + keyword + "%");
        wrapper.orderBy(true, "asc".equalsIgnoreCase(sort), "metadata->>'createdAt'");
        Page<VectorRecord> page = vectorRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.from(page);
    }

    /** 按向量 id 批量删除 */
    public VectorDeleteResult deleteByIds(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new VectorDeleteResult(0);
        }
        return new VectorDeleteResult(vectorRecordMapper.deleteByStringIds(ids));
    }

    /** 按血缘id整批删除 */
    public VectorDeleteResult deleteByLineageId(String lineageId) {
        if (!StringUtils.hasText(lineageId)) {
            throw new IllegalArgumentException("lineageId 不能为空");
        }
        QueryWrapper<VectorRecord> wrapper = new QueryWrapper<>();
        wrapper.apply("metadata->>'lineageId' = {0}", lineageId);
        return new VectorDeleteResult(vectorRecordMapper.delete(wrapper));
    }

    private class ExcelRowListener extends AnalysisEventListener<Map<Integer, String>> {

        private final String lineageId;
        private final String fileName;
        private final String contentColumn;
        private final Map<Integer, String> headers = new LinkedHashMap<>();
        private final List<Document> buffer = new ArrayList<>(BATCH_SIZE);
        private int inserted;
        private int skipped;
        private int rowCount;

        private ExcelRowListener(String lineageId, String fileName, String contentColumn) {
            this.lineageId = lineageId;
            this.fileName = fileName;
            this.contentColumn = contentColumn;
        }

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            headers.clear();
            headers.putAll(headMap);
            if (StringUtils.hasText(contentColumn) && !headers.containsValue(contentColumn)) {
                throw new IllegalArgumentException("Excel 表头中不存在列: " + contentColumn);
            }
        }

        @Override
        public void invoke(Map<Integer, String> row, AnalysisContext context) {
            rowCount++;
            if (rowCount > MAX_UPLOAD_ROWS) {
                throw new IllegalArgumentException("Excel 数据行数超过 " + MAX_UPLOAD_ROWS + " 行，请拆分后上传");
            }
            String content = buildContent(row);
            if (!StringUtils.hasText(content)) {
                skipped++;
                return;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("lineageId", lineageId);
            metadata.put("fileName", fileName);
            metadata.put("sheetName", context.readSheetHolder().getSheetName());
            metadata.put("rowIndex", context.readRowHolder().getRowIndex() + 1);
            metadata.put("createdAt", CREATED_AT_FORMAT.format(Instant.now()));
            buffer.add(new Document(UUID.randomUUID().toString(), content, metadata));
            if (buffer.size() >= BATCH_SIZE) {
                flush();
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            flush();
        }

        private String buildContent(Map<Integer, String> row) {
            if (StringUtils.hasText(contentColumn)) {
                for (Map.Entry<Integer, String> header : headers.entrySet()) {
                    if (contentColumn.equals(header.getValue())) {
                        return row.get(header.getKey());
                    }
                }
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (Integer column : new TreeSet<>(row.keySet())) {
                String value = row.get(column);
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String header = headers.get(column);
                if (StringUtils.hasText(header)) {
                    sb.append(header).append(": ");
                }
                sb.append(value).append('\n');
            }
            return sb.toString().stripTrailing();
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            vectorStore.add(buffer);
            inserted += buffer.size();
            buffer.clear();
        }
    }
}