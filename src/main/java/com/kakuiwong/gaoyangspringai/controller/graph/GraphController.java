package com.kakuiwong.gaoyangspringai.controller.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import lombok.extern.slf4j.Slf4j;
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

            if (snapshot != null && snapshot.next() != null && !snapshot.isEND()) {
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
            @RequestParam String approval) {
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            
            // 获取当前状态
            StateSnapshot snapshot = graph.getState(config);

            if (snapshot == null || snapshot.state() == null) {
                log.warn("未找到执行状态, threadId: {}", threadId);
                return "未找到执行状态";
            }
            
            // 打印当前状态信息用于调试
            log.info("当前状态 - node: {}, next: {}, isEND: {}", 
                    snapshot.node(), snapshot.next(), snapshot.isEND());

            // 检查是否已经执行完成
            if (snapshot.isEND()) {
                log.info("工作流已执行完成, threadId: {}", threadId);
                return snapshot.state().value("result", String.class).orElse("已完成");
            }

            // 恢复执行（传入 null 表示从中断点继续执行）
            RunnableConfig updateState = graph.updateState(config,(Map<String, Object>) null);
            Optional<OverAllState> invoke = graph.invoke((Map<String, Object>) null, updateState);

            String result = invoke.flatMap(state -> state.value("result", String.class))
                    .orElse("恢复执行完成");
            log.info("恢复执行完成, result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("恢复执行失败, threadId: {}", threadId, e);
            return "恢复失败: " + e.getMessage();
        }
    }
}
