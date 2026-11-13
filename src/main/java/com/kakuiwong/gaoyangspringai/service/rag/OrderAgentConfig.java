package com.kakuiwong.gaoyangspringai.service.rag;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.kakuiwong.gaoyangspringai.ai.agent.ModelCallLoggingHook;
import com.kakuiwong.gaoyangspringai.ai.tools.OrderTool;
import com.kakuiwong.gaoyangspringai.ai.tools.ProductSearchTool;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @author: gaoyang
 * @Description: 订单导购 Agent：RAG 商品检索 + 创建订单人工审批
 */
@Configuration
public class OrderAgentConfig {

    @Bean
    public ReactAgent orderReactAgent(DashScopeChatModel dashScopeChatModel,
                                      ProductSearchTool productSearchTool,
                                      OrderTool orderTool,
                                      PostgresSaver postgresSaver) {
        HumanInTheLoopHook humanHook = HumanInTheLoopHook.builder()
                .approvalOn(OrderTool.CREATE_ORDER_TOOL, ToolConfig.builder()
                        .description("创建订单涉及真实交易，需人工确认商品、价格与数量")
                        .build())
                .build();

        return ReactAgent.builder()
                .name("orderAgent")
                .model(dashScopeChatModel)
                .systemPrompt("""
                        你是电商导购助手，目标是引导用户完成购买。
                        规则：
                        1. 用户咨询、挑选或比较商品时，必须先调用 searchProducts 检索商品知识库，只依据检索结果介绍商品，禁止编造商品、价格或库存。
                        2. 推荐商品后主动询问用户是否购买，并确认要购买的商品与数量。
                        3. 只有用户明确确认购买、且商品和数量都明确时，才能调用 createOrder。
                        4. createOrder 会进入人工审核：审核通过才算下单成功；被拒绝时向用户说明原因并继续引导。成功后返回订单信息,不赘述其他内容。
                        5. 每轮对话 searchProducts 最多调用 2 次、createOrder 最多调用 1 次，达到上限后不要再调用工具，直接基于已有信息回答。
                        6. 用简体中文简洁回答，不要假设订单一定创建成功。
                        """)
                .methodTools(productSearchTool, orderTool)
                .hooks(humanHook, ModelCallLimitHook.builder().runLimit(10).build(),
                        new ModelCallLoggingHook())
                .saver(postgresSaver)
                .toolExecutionTimeout(Duration.ofMinutes(3))
                .build();
    }
}
