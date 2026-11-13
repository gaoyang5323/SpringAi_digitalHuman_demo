package com.kakuiwong.gaoyangspringai.service.rag;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.kakuiwong.gaoyangspringai.ai.tools.OrderTool;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderApprovalRequest;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderChatRequest;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderChatResult;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderChatResult.PendingApproval;
import com.kakuiwong.gaoyangspringai.entity.rag.OrderItem;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * @author: gaoyang
 * @Description: 订单导购对话：Agent 检索商品并引导下单，创建订单前由人工审批
 */
@Service
public class OrderChatService {

    private final ReactAgent orderReactAgent;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderChatService(@Qualifier("orderReactAgent") ReactAgent orderReactAgent) {
        this.orderReactAgent = orderReactAgent;
    }

    /** 对话；sessionId 作为 graph threadId，保持同一会话的多轮上下文 */
    public OrderChatResult chat(OrderChatRequest request) throws GraphRunnerException {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(request.sessionId())
                .build();
        return toResult(invoke(request.msg(), config));
    }

    /** 人工审批：批准 / 拒绝 / 修改数量后继续执行 createOrder */
    public OrderChatResult approval(OrderApprovalRequest request) throws GraphRunnerException {
        if (!StringUtils.hasText(request.sessionId())) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (request.decision() == null) {
            throw new IllegalArgumentException("decision 不能为空，可选 APPROVE / REJECT / EDIT");
        }

        ToolFeedback feedback = buildFeedback(request);
        InterruptionMetadata approval = InterruptionMetadata.builder()
                .addToolFeedback(feedback)
                .build();
        RunnableConfig config = RunnableConfig.builder()
                .threadId(request.sessionId())
                .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approval)
                .build();
        // 空串不会追加用户消息（Messageutils 对空串返回空列表），仅从检查点恢复
        return toResult(invoke("", config));
    }

    private ToolFeedback buildFeedback(OrderApprovalRequest request) {
        // Ollama 工具调用可能不带 id，此时按空 id 匹配挂起中的 createOrder
        ToolFeedback.Builder builder = ToolFeedback.builder()
                .id(request.toolCallId() == null ? "" : request.toolCallId())
                .name(OrderTool.CREATE_ORDER_TOOL);

        return switch (request.decision()) {
            case APPROVE -> builder
                    .result(ToolFeedback.FeedbackResult.APPROVED)
                    .build();
            case REJECT -> builder
                    .result(ToolFeedback.FeedbackResult.REJECTED)
                    .description(StringUtils.hasText(request.reason()) ? request.reason() : "人工审核拒绝，用户取消本次购买")
                    .build();
            case EDIT -> {
                if (CollectionUtils.isEmpty(request.products())) {
                    throw new IllegalArgumentException("修改订单时 products 不能为空");
                }
                validateProducts(request.products());
                builder.result(ToolFeedback.FeedbackResult.EDITED)
                        .arguments(objectMapper.writeValueAsString(Map.of("products", request.products())));
                yield builder.build();
            }
        };
    }

    private void validateProducts(List<OrderItem> products) {
        for (OrderItem item : products) {
            if (item == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new IllegalArgumentException("商品数量必须大于 0");
            }
        }
    }

    private NodeOutput invoke(String input, RunnableConfig config) throws GraphRunnerException {
        return orderReactAgent.invokeAndGetOutput(input, config)
                .orElseThrow(() -> new IllegalStateException("订单 Agent 未返回结果"));
    }

    private OrderChatResult toResult(NodeOutput output) {
        String answer = lastAssistantText(output.state());
        if (output instanceof InterruptionMetadata interruption) {
            List<ToolFeedback> feedbacks = interruption.toolFeedbacks();
            if (CollectionUtils.isEmpty(feedbacks)) {
                return OrderChatResult.completed(answer);
            }
            ToolFeedback feedback = feedbacks.get(0);
            return OrderChatResult.waitingApproval(answer, new PendingApproval(
                    feedback.getId(),
                    feedback.getName(),
                    feedback.getArguments(),
                    feedback.getDescription()
            ));
        }
        return OrderChatResult.completed(answer);
    }

    /** 取最后一条助手消息：等待审批时是本轮工具调用消息，结束时是本轮最终回答 */
    private String lastAssistantText(OverAllState state) {
        List<?> messages = state.value("messages", List.class).orElse(List.of());
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage assistantMessage) {
                return assistantMessage.getText() == null ? "" : assistantMessage.getText();
            }
        }
        return "";
    }
}
