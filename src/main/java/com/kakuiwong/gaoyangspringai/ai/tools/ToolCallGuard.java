package com.kakuiwong.gaoyangspringai.ai.tools;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

/**
 * @author: gaoyang
 * @Description: 工具调用次数限制，计数存放在本轮 RunnableConfig 的 context 中，随请求开始重新计数
 */
final class ToolCallGuard {

    private ToolCallGuard() {
    }

    /**
     * 记录一次调用，并判断本轮是否已超过上限。
     * 返回 true 时调用方不应再执行真实业务逻辑，直接返回提示文本。
     */
    static boolean exceeded(ToolContext toolContext, String countKey, int limit) {
        RunnableConfig config = ToolContextHelper.getConfig(toolContext).orElse(null);
        if (config == null) {
            return false;
        }
        Map<String, Object> context = config.context();
        Object count = context.merge(countKey, 1, (oldValue, newValue) -> (Integer) oldValue + (Integer) newValue);
        return (Integer) count > limit;
    }
}
