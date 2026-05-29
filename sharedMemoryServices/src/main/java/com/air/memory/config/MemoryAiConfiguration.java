package com.air.memory.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MemoryAiConfiguration {

    private final ChromaDbClientConfig chromaDbClientConfig;
    private final ChatModelApiKeyConfig chatModelApiKeyConfig;

    private final RestClient.Builder restClientBuilder;

    // ==================== DashScope API（唯一一个） ====================
    @Bean
    public DashScopeApi dashScopeApi() {
        String dashScopeApiKey = chatModelApiKeyConfig.getApiKey().get("qwen");
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    // ==================== ChatModel（用于清洗引擎） ====================
    @Bean
    public DashScopeChatModel chatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-turbo")
                        .temperature(0.3)
                        .maxToken(1024)
                        .build())
                .build();
    }

    // ==================== EmbeddingModel（用于向量化） ====================
    @Bean
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeEmbeddingOptions.builder()
                        .withModel(chromaDbClientConfig.getModelName())
                        .build())
                .build();
    }


    // ==================== Chroma 相关 ====================
    /**
     * ChromaApi Bean（底层 HTTP 客户端）
     */
    @Bean
    public ChromaApi chromaApi(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        String host = chromaDbClientConfig.getClient().getHost();
        int port = chromaDbClientConfig.getClient().getPort();
        String chromaUrl = host + ":" + port;

        var chromaApi = new ChromaApi(chromaUrl, restClientBuilder, objectMapper);

        String keyToken = chromaDbClientConfig.getClient().getKeyToken();
        if (StringUtils.hasText(keyToken)) {
            chromaApi.withKeyToken(keyToken);
        }
        return chromaApi;
    }

    /**
     * ChromaVectorStore Bean
     */
    @Bean
    public ChromaVectorStore chromaVectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(chromaDbClientConfig.getKnowledgeBaseCollection())
                .tenantName(chromaDbClientConfig.getTenantName())
                .databaseName(chromaDbClientConfig.getDatabaseName())
                .initializeSchema(true)
                .build();
    }

    @Bean
    public RestClient chromaDegradeLowRestClient(){
        String chromaUrl = chromaDbClientConfig.getClient().getHost() + ":" + chromaDbClientConfig.getClient().getPort();
        String keyToken = chromaDbClientConfig.getClient().getKeyToken();
        return restClientBuilder
                .baseUrl(chromaUrl)
                .defaultHeader("X-Chroma-Token", keyToken)
                .build();
    }
}
