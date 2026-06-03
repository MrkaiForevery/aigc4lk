package com.air.customerServiceAgent.configuration;

import com.air.customerServiceAgent.config.ChatModelApiKeyConfig;
import com.air.customerServiceAgent.mcp.RemoteMcpToolProvider;
import com.air.customerServiceAgent.tools.CustomerServiceMemoryTools;
import com.air.customerServiceAgent.tools.OrderQueryTools;
import com.air.customerServiceAgent.tools.RoutePolicyTools;
import com.air.customerServiceAgent.tools.TicketQueryTools;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CustomerServiceAgentConfiguration {

    private final ChatModelApiKeyConfig chatModelApiKeyConfig;

    private final CustomerServiceMemoryTools customerServiceMemoryTools;
    private final OrderQueryTools orderQueryTools;
    private final RoutePolicyTools routePolicyTools;
    private final TicketQueryTools ticketQueryTools;

    private final RemoteMcpToolProvider remoteMcpToolProvider;

    @Bean
    public DashScopeApi dashScopeApi() {
        String dashScopeApiKey = chatModelApiKeyConfig.getApiKey().get("qwen");
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    @Bean
    public ChatModel chatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-max")
                        .temperature(0.7)
                        .maxToken(4096)
                        .build())
                .build();
    }

    /**
     * ReactAgent Bean —— A2A 注册核心
     */
    @Bean(name = "customer-service-agent")
    public ReactAgent customerServiceAgent(ChatModel chatModel) {

        // 将自己内部服务的工具对象包装为 ToolCallbackProvider
        ToolCallbackProvider customerServiceAgentToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(customerServiceMemoryTools,orderQueryTools, routePolicyTools,ticketQueryTools)
                .build();

        return ReactAgent.builder()
                .name("customerServiceAgent")
                .model(chatModel)
                .description("智能客服助手，可进行问题路由、订单查询、工单查询")
                .instruction("""
                       你是一个专业的智能客服智能体。
                       
                       ## 核心能力
                       1. **问题分类与路由**：根据用户输入识别问题类型（售后/技术/投诉/其他），然后调用相应的处理策略。
                       2. **订单查询**：当用户提供订单号时，调用 orderQueryTool 查询订单状态、物流信息。
                       3. **工单查询**：当用户询问历史工单或进度时，调用 ticketQueryTool 获取工单信息。
                       4. **用户画像获取**：调用 getProfile 了解用户基本信息（如会员等级、历史购买记录）。

                       ## 工作流程
                       1. 首先调用 getProfile 获取用户画像，以便提供个性化服务。
                       2. 根据用户问题，调用 classify 工具进行意图分类（或直接使用 LLM 判断）。
                       3. 根据分类结果，调用对应的业务工具（订单查询、工单查询）或提供路由建议。
                       4. 用友好、专业的语气回复用户，必要时提供进一步帮助的引导。

                       ## 注意事项
                       - 如果用户情绪激动（投诉类），优先安抚并提供升级渠道。
                       - 工具调用失败时，应告知用户并建议人工客服。
                       - 禁止编造订单或工单信息，必须依赖工具返回的真实数据。
                        """)
                .toolCallbackProviders(customerServiceAgentToolProvider, remoteMcpToolProvider)   // 注入工具类
                .saver(new MemorySaver())
                .build();
    }
}