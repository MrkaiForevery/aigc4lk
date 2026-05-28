package com.air.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "memory.dedup")
public class DedupEnableConfig {
    private boolean enabled = true;
    private double knowledgeThreshold = 0.90;
    private double behaviorThreshold = 0.95;
}