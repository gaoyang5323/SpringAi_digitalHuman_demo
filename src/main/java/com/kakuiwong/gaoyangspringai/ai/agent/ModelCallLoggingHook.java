package com.kakuiwong.gaoyangspringai.ai.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author: gaoyang
 * @Description: 打印每次大模型调用的输入消息与输出内容
 */
@Slf4j
public class ModelCallLoggingHook extends ModelHook {

    private static final String CALL_SEQ_KEY = "__model_call_log_seq__";

    @Override
    public String getName() {
        return "ModelCallLoggingHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        List<?> messages = state.value("messages", List.class).orElse(List.of());
        Object seq = config.context().merge(CALL_SEQ_KEY, 1, (oldValue, newValue) -> (Integer) oldValue + (Integer) newValue);
        log.info("========== LLM 调用 #{} 开始，输入消息 {} 条 ==========", seq, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            log.info("LLM 输入 [{}] {}", i + 1, describe(messages.get(i)));
        }
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        List<?> messages = state.value("messages", List.class).orElse(List.of());
        Object output = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        log.info("========== LLM 调用输出 ==========");
        log.info("LLM 输出 {}", describe(output));
        return CompletableFuture.completedFuture(Map.of());
    }

    private String describe(Object message) {
        if (message instanceof AssistantMessage assistant) {
            String toolCalls = assistant.getToolCalls().stream()
                    .map(toolCall -> toolCall.name() + "(" + toolCall.arguments() + ")")
                    .collect(Collectors.joining("; "));
            return "assistant: " + text(assistant.getText())
                    + (toolCalls.isEmpty() ? "" : " | toolCalls: " + toolCalls);
        }
        if (message instanceof ToolResponseMessage toolResponse) {
            return "tool: " + toolResponse.getResponses().stream()
                    .map(response -> response.name() + " => " + response.responseData())
                    .collect(Collectors.joining(" | "));
        }
        if (message instanceof Message m) {
            return m.getMessageType() + ": " + text(m.getText());
        }
        return String.valueOf(message);
    }

    private String text(String text) {
        return text == null ? "" : text;
    }
}
