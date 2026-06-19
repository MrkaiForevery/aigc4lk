package com.air.commander.config;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.air.commander.configloader.properties.ChromaDbClientProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChromaConfig {

    private final static String QWEN3_EMBEDDING_NAME = "qwen3-embedding:0.6b";

    private final RemoteConfigLoader remoteConfigLoader;


    @Bean("localOllamaEmbeddingModel")
    public EmbeddingModel localOllamaEmbeddingModel(OllamaApi localOllamaApi) {
        return OllamaEmbeddingModel.builder()
                .ollamaApi(localOllamaApi)
                .defaultOptions(OllamaEmbeddingOptions.builder()
                        .model(QWEN3_EMBEDDING_NAME)
                        .build())
                .build();
    }

    /**
     * 创建 ChromaApi Bean，用于与 ChromaDB 通信
     */
    @Bean
    public ChromaApi chromaApi(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        ChromaDbClientProperties chromaDbClientProperties = remoteConfigLoader.getChromaDbClientProperties();
        // 1. 构建 ChromaDB 连接 URL
        String host = chromaDbClientProperties.getClient().getHost();
        int port = chromaDbClientProperties.getClient().getPort();
        String chromaUrl = host + ":" + port;

        // 2. 创建 ChromaApi 实例
        var chromaApi = new ChromaApi(chromaUrl, restClientBuilder, objectMapper);

        // 3. 设置认证凭据(如果是云端向量数据库，则需要设置访问的keyToken)
        String keyToken = chromaDbClientProperties.getClient().getKeyToken();
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
    public ChromaVectorStore chromaVectorStore(ChromaApi chromaApi,
                                               @Qualifier("localOllamaEmbeddingModel") EmbeddingModel localOllamaEmbeddingModel) {
        ChromaDbClientProperties props = remoteConfigLoader.getChromaDbClientProperties();
        ChromaVectorStore.Builder theBuilder = ChromaVectorStore.builder(chromaApi, localOllamaEmbeddingModel);

        Optional.ofNullable(props.getTenantName()).ifPresent(theBuilder::tenantName);
        Optional.ofNullable(props.getDatabaseName()).ifPresent(theBuilder::databaseName);

        return theBuilder.collectionName(props.getCollectionName())
                .initializeSchema(true) // 自动初始化集合
                .build();
    }
}
