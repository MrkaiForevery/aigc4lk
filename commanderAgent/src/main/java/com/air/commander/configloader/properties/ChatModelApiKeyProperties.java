package com.air.commander.configloader.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import java.util.Map;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "customer.model")
public class ChatModelApiKeyProperties {
    private Map<String, String> apiKey;
}