package com.air.commander.configloader.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "spring.ai.vectorstore.chroma")
public class ChromaDbClientProperties {

    private Client client = new Client();
    private String tenantName;
    private String databaseName;
    private String collectionName;

    @Data
    public static class Client {
        private String host ;
        private int port;
        private String keyToken;
    }
}
