package com.air.commander.configloader.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.Map;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "logging")
public class LoggingProperties {
    private Map<String, String> level;
    private Map<String, String> pattern;
}