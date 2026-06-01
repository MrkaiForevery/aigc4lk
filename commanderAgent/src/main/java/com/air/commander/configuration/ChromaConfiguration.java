package com.air.commander.configuration;

import com.air.commander.config.ChromaDbClientConfig;
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
public class ChromaConfiguration {


    private final ChromaDbClientConfig chromaDbClientConfig;
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
