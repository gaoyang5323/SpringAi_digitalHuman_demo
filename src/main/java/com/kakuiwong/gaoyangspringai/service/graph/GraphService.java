package com.kakuiwong.gaoyangspringai.service.graph;

import ch.qos.logback.core.testUtil.RandomUtil;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.*;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.kakuiwong.gaoyangspringai.ai.tools.LocalWeatherTool;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Configuration
public class GraphService {

    public final static String HUMAN_FEEDBACK = "human_feedback";

    @Autowired
    ChatClient ttsChatClient;
    @Autowired
    LocalWeatherTool weatherTool;
    @Autowired
    PostgresSaver postgresSaver;

    @Bean
    public CompiledGraph graph() throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () ->
                Map.of("chatNodeWithoutTool", new ReplaceStrategy(),
                        "chatNodeWithTool", new ReplaceStrategy(),
                        "query", new ReplaceStrategy(),
                        "printNode", new ReplaceStrategy(),
                        "routerNode", new ReplaceStrategy(),
                        "result", new ReplaceStrategy(),
                        "messages", new ReplaceStrategy(),
                        "approval", new ReplaceStrategy()
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
          /*  String response = ttsChatClient.prompt()
                    .user(query)
                    .call()
                    .content();*/
            return Map.of("result", "没用工具->" + query + RandomUtils.secure().randomInt());
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
        stateGraph.addNode(HUMAN_FEEDBACK, new InterruptableNodeAction(HUMAN_FEEDBACK, "等待用户输入"));

        stateGraph.addEdge(StateGraph.START, "printNode");
        stateGraph.addConditionalEdges("printNode", routerEdgeAction,
                Map.of("chatNodeWithTool-Action", "chatNodeWithTool",
                        "chatNodeWithoutTool-Action", "chatNodeWithoutTool"));

        stateGraph.addEdge("chatNodeWithTool", StateGraph.END);


        stateGraph.addEdge("chatNodeWithoutTool", HUMAN_FEEDBACK);
      /*  stateGraph.addConditionalEdges(HUMAN_FEEDBACK, evalHumanFeedback,
                Map.of("ok", "endPrintNode",
                        "rollback", HUMAN_FEEDBACK));*/

        stateGraph.addEdge(HUMAN_FEEDBACK, "endPrintNode");

        stateGraph.addEdge("endPrintNode", StateGraph.END);

        return stateGraph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(postgresSaver)
                        .build())
                //.interruptBefore("endPrintNode")
                .build());
    }

    public static class InterruptableNodeAction implements AsyncNodeActionWithConfig, InterruptableAction {
        private final String nodeId;
        private final String message;

        public InterruptableNodeAction(String nodeId, String message) {
            this.nodeId = nodeId;
            this.message = message;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
            // 正常节点逻辑：更新状态
            return CompletableFuture.completedFuture(Map.of("messages", message));
        }

        @Override
        public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
            // 如果状态中没有 approval，则中断等待用户输入
            Optional<Object> approval = state.value("approval");

            if (approval.isEmpty()) {
                // 返回 InterruptionMetadata 来中断执行
                InterruptionMetadata interruption = InterruptionMetadata.builder(nodeId, state)
                        .addMetadata("message", "等待用户输入...")
                        .addMetadata("node", nodeId)
                        .build();

                return Optional.of(interruption);
            }

            // 如果已经有 approval，继续执行
            return Optional.empty();
        }
    }
}
