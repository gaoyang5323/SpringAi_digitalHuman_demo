package com.kakuiwong.gaoyangspringai.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.stream.Collectors;

import java.util.List;

/**
 * @author: gaoyang
 * @Description: 商品知识库检索工具，基于 PG 向量库做 RAG 检索
 */
@Slf4j
@Component
public class ProductSearchTool {

    private static final int TOP_K = 5;

    /** 每轮对话最多检索 2 次，防止模型在空结果时反复检索 */
    private static final int MAX_CALLS_PER_RUN = 2;

    private static final String CALL_COUNT_KEY = "__product_search_tool_call_count__";

    private static final String LIMIT_REACHED_MESSAGE = "商品检索次数已达上限（每轮最多 2 次），本次不再执行检索。"
            + "请立即基于已获取的检索结果回答用户；若结果为空，请引导用户换一种描述。";

    private final VectorStore vectorStore;

    public ProductSearchTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(name = "searchProducts", description = "从商品知识库中检索商品信息（名称、价格、卖点、规格等）。"
            + "用户咨询、挑选、比较商品或询问购买建议时必须先调用，只依据检索结果回答，禁止编造商品")
    public String searchProducts(@ToolParam(description = "用户的商品需求，例如品类、预算、用途、偏好") String query,
                                 ToolContext toolContext) {
        if (ToolCallGuard.exceeded(toolContext, CALL_COUNT_KEY, MAX_CALLS_PER_RUN)) {
            log.info("调用工具searchProducts已达上限，本次不执行检索");
            return LIMIT_REACHED_MESSAGE;
        }
        log.info("调用工具searchProducts, query={}", query);
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(TOP_K)
                        .build()
        );
        if (CollectionUtils.isEmpty(documents)) {
            return "商品知识库中未检索到相关商品，请引导用户换一种描述。";
        }
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }
}
