package com.air.imageAnalysis.configuration;

import com.air.imageAnalysis.config.ChatModelApiKeyConfig;
import com.air.imageAnalysis.mcp.RemoteMcpToolProvider;
import com.air.imageAnalysis.tools.ImageAnalysisMemoryTools;
import com.air.imageAnalysis.tools.ImageRecognitionTool;
import com.air.imageAnalysis.tools.OCRTools;
import com.air.imageAnalysis.tools.SceneUnderstandingTool;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ImageAnalysisAgentConfiguration {

    private final ChatModelApiKeyConfig chatModelApiKeyConfig;

    // 手动构造器，只注入配置属性类
    public ImageAnalysisAgentConfiguration(ChatModelApiKeyConfig chatModelApiKeyConfig) {
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
    @Qualifier("ocrChatModel")
    public ChatModel OcrChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-vl-max")
                        .withMultiModel(true)
                        .temperature(0.2)
                        .maxToken(4096)
                        .build())
                .build();
    }

    @Bean
    @Qualifier("sceneChatModel")
    public ChatModel sceneChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-vl-max")
                        .withMultiModel(true)
                        .temperature(0.6)            // 更高的温度激发创意
                        .topP(0.85)
                        .maxToken(4096)
                        .build())
                .build();
    }

    /**
     * ReactAgent Bean —— A2A 注册核心
     */
    @Bean(name = "image-analysis-agent")
    public ReactAgent imageAnalysisAgent(
            @Qualifier("ocrChatModel") ChatModel chatModel,
            ImageAnalysisMemoryTools imageAnalysisMemoryTools,
            ImageRecognitionTool imageRecognitionTool,
            OCRTools ocrTools,
            SceneUnderstandingTool sceneUnderstandingTool,
            RemoteMcpToolProvider remoteMcpToolProvider
    ) {

        // 将自己内部服务的工具对象包装为 ToolCallbackProvider
        ToolCallbackProvider imageAnalysisAgentToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(imageAnalysisMemoryTools, imageRecognitionTool, ocrTools, sceneUnderstandingTool)
                .build();

        return ReactAgent.builder()
                .name("imageAnalysisAgent")
                .model(chatModel)
                .description("图像分析助手，支持OCR、目标检测、场景理解")
                .instruction("""
                        你是一个专业的图像分析智能体。
                        
                        核心能力：
                        1. OCR文字识别：调用 ocrTool 提取图片中的文字。
                        2. 目标检测：调用 detectObjects 检测物体、人脸等。
                        3. 场景理解：调用 analyzeScene 分析整体场景、情感、光照等。
                        4. 用户画像：调用 getProfile 获取用户信息。
                        
                        工作流程：
                        - 用户提供图片Base64和需求，选择合适的工具。
                        - 用自然语言描述结果，必要时给出建议。
                        
                        注意事项：
                        - 图片过大时先压缩。
                        - 涉及隐私内容需提醒用户。
                        """)
                .toolCallbackProviders(imageAnalysisAgentToolProvider, remoteMcpToolProvider)   // 注入工具类
                .saver(new MemorySaver())
                .build();
    }
}