package com.air.commander.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "customer.model")
public class ChatModelApiKeyConfig {
    private Map<String, String> apiKey;

    public Map<String, String> getApiKey() {
        return apiKey;
    }
    public void setApiKey(Map<String, String> apiKey) {
        this.apiKey = apiKey;
    }
}