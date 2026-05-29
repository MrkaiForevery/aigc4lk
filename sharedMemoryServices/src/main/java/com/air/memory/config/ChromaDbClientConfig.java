package com.air.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "spring.ai.vectorstore.chroma")
public class ChromaDbClientConfig {

    private Client client = new Client();
    private String tenantName = "default_tenant";
    private String databaseName = "default_database";
    private String knowledgeBaseCollection = "knowledge_base";
    private String knowledgeArchiveCollection = "knowledge_archive";
    private String modelName = "text-embedding-v2";

    @Data
    public static class Client {
        private String host = "http://localhost";
        private int port = 8000;
        private String keyToken;
    }
}
