package com.air.commander.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class CommanderChatConfiguration {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String defaultModelName;

    /**
     * 模型缓存
     */
    @Bean
    public Map<String, ChatModel> modelCache() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 意图分类 ChatClient
     */
    @Bean
    public ChatClient intentClassificationClient(
            @Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultSystem("""
                你是一个专业的意图分类专家。请分析用户输入，识别以下信息：
                
                1. 场景类型 (scenario):
                   - DOCUMENT_GENERATION: 文档生成、报告撰写
                   - MARKET_ANALYSIS: 市场分析、数据洞察
                   - INVESTMENT_DECISION: 投资决策、风险评估
                   - CUSTOMER_SERVICE: 客服咨询、投诉建议
                   - IMAGE_ANALYSIS: 图像识别、图片分析
                   - SPEECH_RECOGNITION: 语音识别、音频处理
                   - VIDEO_ANALYSIS: 视频分析、内容摘要
                   - CODE_REVIEW: 代码审查
                   - GENERAL: 通用任务
                
                2. 复杂度评估 (complexity): HIGH, MEDIUM, LOW
                
                3. 所需能力 (required_capabilities): REASONING, ANALYSIS, CODING, CHAT, FAST_RESPONSE
                
                4. 输入模态 (modality): TEXT, IMAGE, AUDIO, VIDEO
                
                请以JSON格式返回结果，不要包含其他内容。
                {
                    "scenario": "场景类型",
                    "complexity": "复杂度",
                    "required_capabilities": ["能力列表"],
                    "modality": "输入模态",
                    "confidence": 0.0-1.0
                }
                """)
            .build();
    }




    /**
     * 2. 再基于 DashScopeApi 创建 ChatModel Bean [citation:3][citation:4]
     * 注意：必须先注入 dashScopeApi() 创建好的 Bean
     */
    @Bean
    public DashScopeChatModel dashScopeChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)  //传入 DashScopeApi 实例
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(defaultModelName) // 必须设置默认的模型名称
                        .temperature(0.7)
                        .maxToken(2048)
                        .build())
                .build();
    }

    /**
     * 1. 先创建 DashScopeApi Bean [citation:4]
     * 这是接入 DashScope 模型服务的基础客户端
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }
}