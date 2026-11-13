package com.kakuiwong.gaoyangspringai;

import com.alibaba.excel.EasyExcel;
import com.kakuiwong.gaoyangspringai.entity.rag.VectorUploadResult;
import com.kakuiwong.gaoyangspringai.mapper.rag.VectorRecordMapper;
import com.kakuiwong.gaoyangspringai.service.rag.VectorManageService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorManageUploadLimitTest {

    private final VectorStore vectorStore = Mockito.mock(VectorStore.class);
    private final VectorManageService service =
            new VectorManageService(vectorStore, Mockito.mock(VectorRecordMapper.class));

    @Test
    void rejectsMoreThan50Rows() {
        MockMultipartFile file = xlsx(51);
        Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.uploadExcel(file, null, null));
        assertTrue(rootMessage(e).contains("超过 50 行"), "实际消息: " + rootMessage(e));
        assertTrue(String.valueOf(e.getMessage()).contains("超过 50 行"), "顶层消息: " + e.getMessage());
        Mockito.verify(vectorStore, Mockito.never()).add(Mockito.anyList());
    }

    @Test
    void acceptsExactly50Rows() {
        MockMultipartFile file = xlsx(50);
        VectorUploadResult result = service.uploadExcel(file, null, null);
        assertEquals(50, result.getInserted());
        Mockito.verify(vectorStore, Mockito.times(1)).add(Mockito.anyList());
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return String.valueOf(cur.getMessage());
    }

    private MockMultipartFile xlsx(int rows) {
        List<List<String>> data = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            data.add(List.of("标题" + i, "内容" + i));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).head(List.of(List.of("title"), List.of("content")))
                .sheet("s").doWrite(data);
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }
}
