package com.air.commander.config;

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
    private String collectionName = "default_collection";

    @Data
    public static class Client {
        private String host = "http://localhost";
        private int port = 8000;
        private String keyToken;
    }
}
