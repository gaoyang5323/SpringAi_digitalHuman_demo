package com.kakuiwong.gaoyangspringai.service.graph;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.kakuiwong.gaoyangspringai.ai.tools.HUMANFEEDBACKTool;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class ReactAgentGraphService {

    @Autowired
    OllamaChatModel miniOllamaChatModel;
    @Autowired
    HUMANFEEDBACKTool humanfeedbackTool;
    @Autowired
    PostgresSaver postgresSaver;

    @Bean
    public ReactAgent reactAgentGraphHuman() {
        //人工反馈
        HumanInTheLoopHook humanHook = HumanInTheLoopHook.builder()
                .approvalOn("refund", ToolConfig.builder()
                        .description("退款操作，涉及金额和订单，需人工审批")
                        .build())
                .build();

        //摘要
        SummarizationHook summarizationHook = SummarizationHook.builder()
                .model(miniOllamaChatModel)
                .maxTokensBeforeSummary(4000)
                .messagesToKeep(6)
                .build();

        return ReactAgent.builder()
                .systemPrompt("你是订单处理机器人,当用户提问不是订单处理时进行引导,有订单号就直接退款不需要追加提问直接调用" +
                        "工具refund,已处理成功的订单不能重复退单")
                .name("long-task-agent")
                .model(miniOllamaChatModel)
                .methodTools(humanfeedbackTool)
                .hooks(ModelCallLimitHook.builder().runLimit(3).build(),humanHook)
                .saver(postgresSaver)
                .toolExecutionTimeout(Duration.ofMinutes(1))
                .build();
    }
}
