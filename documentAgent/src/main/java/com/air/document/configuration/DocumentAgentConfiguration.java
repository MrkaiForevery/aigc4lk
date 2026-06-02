package com.air.document.configuration;

import com.air.document.config.ChatModelApiKeyConfig;
import com.air.document.tools.DocumentAgentTools;
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

import java.util.List;

@Slf4j
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
                        
                        ## 任务信息
                        你会收到一个包含以下字段的 JSON：
                        - taskType: 任务类型
                        - topic: 文档主题
                        - userId: 用户标识
                        - sessionId: 会话标识
                        - docType: 文档类型
                        
                        ## 任务流程
                        1. 从输入中提取 userId，调用 getProfile(userId) 获取用户画像
                        2. 调用 getPreference(userId) 获取用户偏好
                        3. 调用 searchKnowledge(topic) 搜索相关知识
                        4. 根据获取的信息生成文档
                        
                        ## 兜底策略
                        - 如果 getProfile 返回空或失败，使用默认：技术等级=中级，沟通风格=专业
                        - 如果 getPreference 返回空或失败，使用默认：输出风格=详细
                        - 如果 searchKnowledge 返回空或失败，基于你自己的知识生成
                        - 如果所有工具都失败，直接用默认设置生成文档，不要反复重试
                        
                        ## 行为准则
                        - 不要编造 userId，使用输入中提供的真实 userId
                        - 不要在回复中询问用户"是否继续"或"请提供更多信息"
                        - 不要解释你做了什么工具调用，直接输出文档内容
                        """)
                .toolCallbackProviders(documentToolProvider, mcpToolProvider)   // 注入工具类
                .saver(new MemorySaver())
                .build();
    }
}