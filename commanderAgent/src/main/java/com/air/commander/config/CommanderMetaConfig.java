package com.air.commander.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.commander")
public class CommanderMetaConfig {
    
    private String intentClassificationModel = "qwen-turbo";
    private String defaultArchitecture = "sequential-pipeline";
    private String architectureSelectionStrategy = "hybrid";
    private String modelSelectionStrategy = "weighted-round-robin";
    private boolean enableFallback = true;
    private int maxRetries = 2;
    private int timeoutSeconds = 300;
    private boolean multimodalEnabled = true;
    
    /**
     * 场景到架构的默认映射
     */
    private Map<String, String> scenarioArchitectureMapping = Map.of(
        "DOCUMENT_GENERATION", "sequential-pipeline",
        "MARKET_ANALYSIS", "parallel-analysis",
        "INVESTMENT_DECISION", "debate-system",
        "CUSTOMER_SERVICE", "smart-routing",
        "IMAGE_ANALYSIS", "sequential-pipeline",
        "SPEECH_RECOGNITION", "sequential-pipeline",
        "VIDEO_ANALYSIS", "sequential-pipeline"
    );
    
    /**
     * 复杂度到模型的默认映射
     */
    private Map<String, String> complexityModelMapping = Map.of(
        "HIGH", "qwen-max",
        "MEDIUM", "qwen-plus",
        "LOW", "qwen-turbo"
    );
}