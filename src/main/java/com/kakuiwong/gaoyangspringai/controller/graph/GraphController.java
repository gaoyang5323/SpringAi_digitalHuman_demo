package com.kakuiwong.gaoyangspringai.controller.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * @author: gaoyang
 * @Description:
 */
@RestController
public class GraphController {

    @Autowired
    CompiledGraph graph;

    @RequestMapping("/graph")
    public String chatPage(@RequestParam String msg) {
        Optional<OverAllState> invoke = graph.invoke(Map.of("query", msg), RunnableConfig.builder().build());
        return invoke.flatMap(allState ->
                allState.value("result", String.class)).orElse("");
    }
}
