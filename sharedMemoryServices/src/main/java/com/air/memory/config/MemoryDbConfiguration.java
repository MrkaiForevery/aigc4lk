package com.air.memory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MemoryDbConfiguration {

    private final ChromaDbClientConfig chromaDbClientConfig;
    private final ChatModelApiKeyConfig chatModelApiKeyConfig;

    /**
     * OpenAiApi 客户端
     */
    @Bean
    public OpenAiApi openAiApi() {
        String dashScopeApiKey = chatModelApiKeyConfig.getApiKey().get("openai");
        return OpenAiApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

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
     * 创建 ChromaVectorStore Bean
     */
    @Bean
    public ChromaVectorStore chromaVectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .tenantName(chromaDbClientConfig.getTenantName())
                .databaseName(chromaDbClientConfig.getDatabaseName())
                .collectionName(chromaDbClientConfig.getCollectionName())
                .initializeSchema(true)
                .build();
    }
}
