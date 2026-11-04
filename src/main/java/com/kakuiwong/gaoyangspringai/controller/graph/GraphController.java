package com.kakuiwong.gaoyangspringai.controller.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.kakuiwong.gaoyangspringai.service.graph.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * @author: gaoyang
 * @Description: Graph工作流控制器（支持人工审批）
 */
@Slf4j
@RestController
@RequestMapping("/graph")
public class GraphController {

    @Autowired
    CompiledGraph graph;

    /**
     * 开始执行工作流
     * 当遇到 interruptBefore 配置的节点时会自动中断
     */
    @GetMapping("/start")
    public String start(@RequestParam String msg, @RequestParam String threadId) {
        try {
            Optional<OverAllState> invoke = graph.invoke(
                    Map.of("query", msg),
                    RunnableConfig.builder().threadId(threadId).build()
            );

            // 检查是否在中断点
            StateSnapshot snapshot = graph.getState(
                    RunnableConfig.builder().threadId(threadId).build()
            );

            if (snapshot != null && snapshot.next() != null && !StateGraph.END.equals(snapshot.next())) {
                return "工作流已暂停，等待审批。当前状态: " + snapshot.node();
            }

            return invoke.flatMap(state -> state.value("result", String.class))
                    .orElse("执行完成");
        } catch (Exception e) {
            log.error("工作流执行失败, threadId: {}", threadId, e);
            return "执行失败: " + e.getMessage();
        }
    }

    /**
     * 人工审批通过，恢复执行
     */
    @GetMapping("/resume/{threadId}")
    public String resume(
            @PathVariable String threadId,
            String approval) {
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

            // 获取当前状态
            StateSnapshot snapshot = graph.getState(config);

            if (snapshot == null || snapshot.state() == null) {
                return "未找到执行状态";
            }

            if (StateGraph.END.equals(snapshot.next())) {
                return snapshot.state().value("result", String.class).orElse("已完成");
            }

            if (StringUtils.isBlank(approval) || !approval.equals("ok")) {
                return "审批不执行";
            }

            // 指定 nodeId 为 next() 节点，让框架从下一个节点开始执行
            var updatedConfig = graph.updateState(config, Map.of("approval", approval));

            RunnableConfig resumeConfig = RunnableConfig.builder(updatedConfig)
                    .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, "placeholder")
                    .build();


            graph.invoke((Map<String, Object>) null, resumeConfig);
            return "恢复成功";
        } catch (Exception e) {
            log.error("恢复执行失败, threadId: {}", threadId, e);
            return "恢复失败: " + e.getMessage();
        }
    }
}
