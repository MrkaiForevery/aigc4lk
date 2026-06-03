package com.air.codeReview.configuration;

import com.air.codeReview.config.ChatModelApiKeyConfig;
import com.air.codeReview.mcp.RemoteMcpToolProvider;
import com.air.codeReview.tools.CodeAnalysisAbilityTools;
import com.air.codeReview.tools.CodeReviewMemoryTools;
import com.air.codeReview.tools.GitDiffTools;
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
public class CodeReviewAgentConfiguration {

    private final ChatModelApiKeyConfig chatModelApiKeyConfig;

    private final CodeReviewMemoryTools codeReviewMemoryTools;
    private final CodeAnalysisAbilityTools codeAnalysisAbilityTools;
    private final GitDiffTools gitDiffTools;

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
    @Bean(name = "code-review-agent")
    public ReactAgent codeReviewAgent(ChatModel chatModel) {

        // 将自己内部服务的工具对象包装为 ToolCallbackProvider
        ToolCallbackProvider codeReviewAgentToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(codeReviewMemoryTools,codeAnalysisAbilityTools, gitDiffTools)
                .build();

        return ReactAgent.builder()
                .name("codeReviewAgent")
                .model(chatModel)
                .description("代码审查智能体")
                .instruction("""
                       你是一个专业的代码审查智能体。
        
                       ## 核心能力
                       1. **代码质量分析**：评估代码复杂度、可读性、可维护性、重复代码
                       2. **安全漏洞检测**：识别SQL注入、XSS、硬编码密钥、不安全的反序列化等风险
                       3. **编码规范检查**：检查命名规范、注释规范、格式规范、最佳实践

                       ## 工作流程
                       当你收到代码审查任务时，请按以下步骤执行：
                       1. 调用 getProfile 获取用户画像（了解团队编码习惯）
                       2. 调用 getPreference 获取用户偏好（如关注的检查项）
                       3. 调用 searchKnowledge 搜索相关的编码规范或安全知识
                       4. 使用 analyzeCode 工具对代码进行全面分析
                       5. 使用 checkSecurity 工具专项检测安全问题
                       6. 用自然语言组织审查报告，包含：
                          - 总体评分
                          - 问题清单（按严重程度分级）
                          - 改进建议
                          - 最佳实践提示

                       ## 注意事项
                       - 对于模糊问题，使用默认安全标准
                       - 工具调用失败时，基于已有信息继续分析，不要反复重试
                       - 最终输出应包含具体的代码位置和建议
                        """)
                .toolCallbackProviders(codeReviewAgentToolProvider, remoteMcpToolProvider)   // 注入工具类
                .saver(new MemorySaver())
                .build();
    }
}