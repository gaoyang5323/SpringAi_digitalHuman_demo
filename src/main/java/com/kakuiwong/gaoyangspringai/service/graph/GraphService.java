package com.kakuiwong.gaoyangspringai.service.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.kakuiwong.gaoyangspringai.ai.tools.LocalWeatherTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Configuration
public class GraphService {

    @Autowired
    ChatClient ttsChatClient;
    @Autowired
    LocalWeatherTool weatherTool;

    @Bean
    public CompiledGraph graph() throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () ->
                Map.of("chatNodeWithoutTool", new ReplaceStrategy(),
                        "chatNodeWithTool", new ReplaceStrategy(),
                        "query", new ReplaceStrategy(),
                        "printNode", new ReplaceStrategy(),
                        "routerNode", new ReplaceStrategy(),
                        "result", new ReplaceStrategy()
                );

        AsyncNodeAction printNode = AsyncNodeAction.node_async(state -> {
            String query = state.value("query", String.class).orElse("");
            System.out.println("用户输入->" + query);
            return Map.of();
        });

        // 使用工具的节点
        AsyncNodeAction chatNodeWithTool = AsyncNodeAction.node_async(state -> {
            String query = state.value("query", String.class).orElse("");
            String response = ttsChatClient.prompt()
                    .user(query)
                    .tools(weatherTool)
                    .call()
                    .content();
            return Map.of("result", response);
        });

        // 不使用工具的节点
        AsyncNodeAction chatNodeWithoutTool = AsyncNodeAction.node_async(state -> {
            String query = state.value("query", String.class).orElse("");
            String response = ttsChatClient.prompt()
                    .user(query)
                    .call()
                    .content();
            return Map.of("result", response);
        });

        AsyncNodeAction endPrintNode = AsyncNodeAction.node_async(state -> {
            String query = state.value("query", String.class).orElse("");
            System.out.println("没用工具->" + query);
            return Map.of();
        });

        //路由
        AsyncEdgeAction routerEdgeAction = AsyncEdgeAction.edge_async(state -> {
            String query = state.value("query", String.class).orElse("");
            if (query.contains("天气")) {
                return "chatNodeWithTool-Action";
            } else {
                return "chatNodeWithoutTool-Action";
            }
        });

        StateGraph stateGraph = new StateGraph(keyStrategyFactory);

        stateGraph.addNode("chatNodeWithTool", chatNodeWithTool);
        stateGraph.addNode("chatNodeWithoutTool", chatNodeWithoutTool);
        stateGraph.addNode("printNode", printNode);
        stateGraph.addNode("endPrintNode", endPrintNode);

        stateGraph.addEdge(StateGraph.START, "printNode");
        stateGraph.addConditionalEdges("printNode",routerEdgeAction,
                Map.of("chatNodeWithTool-Action","chatNodeWithTool",
                        "chatNodeWithoutTool-Action","chatNodeWithoutTool"));

        stateGraph.addEdge("chatNodeWithTool", StateGraph.END);

        stateGraph.addEdge("chatNodeWithoutTool", "endPrintNode");
        stateGraph.addEdge("endPrintNode", StateGraph.END);

        return stateGraph.compile();
    }
}
