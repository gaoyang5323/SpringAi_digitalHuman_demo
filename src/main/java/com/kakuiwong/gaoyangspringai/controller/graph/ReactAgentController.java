package com.kakuiwong.gaoyangspringai.controller.graph;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

/**
 * @author: gaoyang
 * @Description: Graph工作流控制器（支持人工审批）
 */
@Slf4j
@RestController
@RequestMapping("/reactAgent")
public class ReactAgentController {

    @Autowired
    ReactAgent reactAgentGraphHuman;

    @GetMapping("/start")
    public String start(@RequestParam String msg, @RequestParam String threadId) throws GraphRunnerException {
        Optional<NodeOutput> result = reactAgentGraphHuman
                .invokeAndGetOutput(msg, RunnableConfig.builder().threadId(threadId).build());


        if (result.get() instanceof InterruptionMetadata interruption) {
            List<ToolFeedbackView> feedbacks = interruption.toolFeedbacks().stream()
                    .map(tf -> new ToolFeedbackView(
                            tf.getId(),
                            tf.getName(),
                            tf.getArguments(),
                            tf.getDescription(),
                            tf.getResult()
                    ))
                    .toList();

            return new ObjectMapper().writeValueAsString(feedbacks);
        }

        OverAllState state = result.get().state();
        List<Message> messages = state.value("messages", List.class)
                .orElse(List.of());

        String aiAnswer = messages.stream()
                .filter(m -> m instanceof AssistantMessage)
                .map(m -> ((AssistantMessage) m).getText())
                .filter(t -> t != null && !t.isBlank())
                .reduce((first, second) -> second)
                .orElse(null);

        return aiAnswer;
    }


    /**
     * 断点恢复执行，通过 approval 判断是否继续后续节点：
     * ok → APPROVED，执行工具并继续后续节点
     * no → REJECTED，不执行工具，把拒绝理由回喂给模型
     * 待审批信息直接从检查点状态（PostgreSQL）读取，不依赖本地缓存
     */
    @GetMapping("/resume/{threadId}")
    public String resume(
            @PathVariable String threadId,
            @RequestParam String approval) throws GraphRunnerException {

        InterruptionMetadata.ToolFeedback feedback =
                InterruptionMetadata.ToolFeedback.builder()
                        .id("")
                        .name("refund")
                        .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                        .build();

        InterruptionMetadata approvalMetadata = InterruptionMetadata.builder()
                .addToolFeedback(feedback)
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata)
                .build();

        Optional<NodeOutput> resumed = reactAgentGraphHuman.invokeAndGetOutput("", config);

        return new ObjectMapper().writeValueAsString(resumed.get());
    }

    public record ToolFeedbackView(
            String id,
            String name,
            String arguments,
            String description,
            InterruptionMetadata.ToolFeedback.FeedbackResult result
    ) {
    }
}

