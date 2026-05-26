package com.air.commander.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.Map;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "platform.logging")
public class LoggingConfig {
    private Map<String, String> level;
    private Map<String, String> pattern;
}