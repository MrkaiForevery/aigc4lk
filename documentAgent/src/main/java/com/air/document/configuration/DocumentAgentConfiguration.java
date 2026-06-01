package com.air.document.configuration;

import com.air.document.config.ChatModelApiKeyConfig;
import com.air.document.tools.DocumentAgentTools;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DocumentAgentConfiguration {

    private final ChatModelApiKeyConfig chatModelApiKeyConfig;
    private final DocumentAgentTools documentAgentTools;

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
    @Bean(name = "document-agent")
    public ReactAgent documentAgent(ChatModel chatModel, ToolCallbackProvider mcpToolProvider) {
        // 将自己内部服务的工具对象包装为 ToolCallbackProvider
        ToolCallbackProvider documentToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(documentAgentTools)
                .build();

        return ReactAgent.builder()
                .name("documentGenerationAgent")
                .model(chatModel)
                .description("文档生成智能体")
                .instruction("""
                        你是一个专业的文档生成智能体。
                        当收到生成文档的任务时，请按以下步骤操作：
                        1. 调用 getProfile 获取用户的技术等级和沟通风格
                        2. 调用 getPreference 获取用户的输出风格偏好
                        3. 调用 searchKnowledge 搜索与文档主题相关的参考知识
                        4. 根据用户的偏好、技术等级、参考知识，生成一份高质量的结构化文档
                        
                        """)
                .toolCallbackProviders(documentToolProvider, mcpToolProvider)   // 注入工具类
                .saver(new MemorySaver())
                .build();
    }
}