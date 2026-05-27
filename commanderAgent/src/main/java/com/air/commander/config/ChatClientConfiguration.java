package com.air.commander.config;

import com.air.commander.model.ChatModelRouter;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.air.platform.common.model.ModelDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatClientConfiguration {

    private final ChatModelApiKeyConfig chatModelApiKeyConfig;
    private final ChatModelRouter chatModelRouter;
    private final ChromaDbClientConfig chromaDbClientConfig;

    /**
     * 初始化的模型缓存bean，整个应用都可以使用
     * 模型缓存：modelId -> ChatModel
     * 支持动态创建和缓存不同类型的模型实例
     */
    @Bean
    public Map<String, ChatModel> modelCache() {
        return new ConcurrentHashMap<>();
    }

    /**
     * DashScope API 客户端（通义系列模型共用）
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        String dashScopeApiKey = chatModelApiKeyConfig.getApiKey().get("qwen");
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 意图分类 ChatClient（固定使用快速模型）
     */
    @Bean
    public ChatClient intentClassificationClient(Map<String, ChatModel> modelCache) {
        ChatModel fastModel = modelCache.computeIfAbsent("qwen-turbo", this::createModel);
        return ChatClient.builder(fastModel)
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
     * 根据 ModelDefinition 动态创建 ChatModel
     */
    public ChatModel createModel(String modelId) {
        //根据模型id，获取模型的配置定义
        ModelDefinition def = chatModelRouter.getModelDefinition(modelId);
        if (def == null) {
            log.warn("Model definition not found for '{}', using built-in fallback", modelId);
            return createFallbackModel();
        }
        return createModel(def);
    }

    private ChatModel createFallbackModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-turbo")
                        .temperature(0.7)
                        .maxToken(2048)
                        .build())
                .build();
    }

    /**
     * 根据 ModelDefinition 创建对应的 ChatModel
     */
    public ChatModel createModel(ModelDefinition def) {
        log.info("Creating ChatModel: {} (type: {})", def.getModelId(), def.getType());
        return switch (def.getType()) {
            case DASHSCOPE -> createDashScopeModel(def);
            case OPENAI_COMPATIBLE -> createOpenAICompatibleModel(def);
            default -> throw new UnsupportedOperationException(
                    "Unsupported model type: " + def.getType());
        };
    }

    /**
     * 创建 DashScope（通义系列）模型
     */
    private ChatModel createDashScopeModel(ModelDefinition def) {
        //复用同一个 API 客户端
        DashScopeApi api = dashScopeApi();
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(def.getModelName())
                        .temperature(0.7)
                        .maxToken(4096)
                        .build())
                .build();
    }

    /**
     * 创建 OpenAI 兼容模型（如 DeepSeek）
     */
    private ChatModel createOpenAICompatibleModel(ModelDefinition def) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(getApiKey(def))  // 需要从配置中获取对应模型的 API Key
                .baseUrl(def.getBaseUrl())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(def.getModelName())
                        .temperature(0.7)
                        .maxTokens(4096)
                        .build())
                .build();
    }

    /**
     * 根据模型提供商获取 API Key
     */
    private String getApiKey(ModelDefinition def) {
        Map<String, String> keyMap = chatModelApiKeyConfig.getApiKey();
        if (keyMap != null && keyMap.containsKey(def.getProvider())) {
            return keyMap.get(def.getProvider());
        }
        // 兜底：如果 provider 不匹配，尝试用 qwen
        return keyMap != null ? keyMap.get("qwen") : "";
    }


    /**
     * 创建 ChromaApi Bean，用于与 ChromaDB 通信
     */
    @Bean
    public ChromaApi chromaApi(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        // 1. 构建 ChromaDB 连接 URL
        String host = chromaDbClientConfig.getClient().getHost();
        int port = chromaDbClientConfig.getClient().getPort();
        String chromaUrl = host + ":" + port;

        // 2. 创建 ChromaApi 实例
        var chromaApi = new ChromaApi(chromaUrl, restClientBuilder, objectMapper);

        // 3. 设置认证凭据
        String keyToken = chromaDbClientConfig.getClient().getKeyToken();
        if (StringUtils.hasText(keyToken)) {
            chromaApi.withKeyToken(keyToken);
        }
        return chromaApi;
    }

    /**
     * todo 这里先暂时不用动态的EmbeddingModel，固定用dashscope类型的
     * 创建 ChromaVectorStore Bean
     */
    @Bean
    public ChromaVectorStore chromaVectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .tenantName(chromaDbClientConfig.getTenantName())
                .databaseName(chromaDbClientConfig.getDatabaseName())
                .collectionName(chromaDbClientConfig.getCollectionName())
                .initializeSchema(true) // 自动初始化集合
                .build();
    }

}