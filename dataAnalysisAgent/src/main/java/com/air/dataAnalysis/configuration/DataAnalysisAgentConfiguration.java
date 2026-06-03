package com.air.dataAnalysis.configuration;

import com.air.dataAnalysis.config.ChatModelApiKeyConfig;
import com.air.dataAnalysis.mcp.RemoteMcpToolProvider;
import com.air.dataAnalysis.tools.DataAnalysisAbilityTools;
import com.air.dataAnalysis.tools.DataAnalysisMemoryTools;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DataAnalysisAgentConfiguration {

    private final ChatModelApiKeyConfig chatModelApiKeyConfig;

    // 手动构造器，只注入配置属性类
    public DataAnalysisAgentConfiguration(ChatModelApiKeyConfig chatModelApiKeyConfig) {
        this.chatModelApiKeyConfig = chatModelApiKeyConfig;
    }

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
    @Bean(name = "data-analysis-agent")
    public ReactAgent dataAnalysisAgent(
            ChatModel chatModel,
            DataAnalysisMemoryTools dataAnalysisMemoryTools,
            DataAnalysisAbilityTools dataAnalysisAbilityTools,
            RemoteMcpToolProvider remoteMcpToolProvider
    ) {

        // 将自己内部服务的工具对象包装为 ToolCallbackProvider
        ToolCallbackProvider dataAnalysisAgentToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(dataAnalysisMemoryTools, dataAnalysisAbilityTools)
                .build();

        return ReactAgent.builder()
                .name("dataAnalysisAgent")
                .model(chatModel)
                .description("数据分析智能体：支持多维度分析、趋势预测、统计摘要")
                .instruction("""
                        你是一个专业的数据分析智能体。
                        
                        ## 核心能力
                        1. **多维度分析**：调用 multiDimensionAnalysis 工具对数据进行切片和钻取
                        2. **趋势预测**：调用 trendForecast 工具基于历史数据预测未来
                        3. **统计摘要**：调用 statisticalSummary 工具计算基本统计量
                        
                        ## 工作流程
                        当你收到数据分析任务时，请按以下步骤执行：
                        1. 调用 getProfile 获取用户画像，了解用户的行业和分析偏好
                        2. 调用 getPreference 获取用户偏好（如常用维度、时间范围）
                        3. 调用 searchKnowledge 搜索相关知识库中的分析模板或案例
                        4. 根据任务类型选择合适的分析工具执行分析
                        5. 用自然语言组织分析结果，包含结论和业务建议
                        
                        ## 注意事项
                        - 如果用户未明确指定维度，默认使用时间维度 + 地区维度
                        - 预测时需注明置信区间和模型假设
                        - 任何工具调用失败时，使用默认值继续分析，不要反复重试
                        - 最终输出应包含清晰的结论和数据支撑
                        """)
                .toolCallbackProviders(dataAnalysisAgentToolProvider, remoteMcpToolProvider)   // 注入工具类
                .saver(new MemorySaver())
                .build();
    }
}