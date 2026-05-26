package com.air.commander.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.HashMap;
import java.util.Map;

/**
 * 从Nacos配置中心中读取platform-commander-config.yaml元数据，并监听配置变化
 */

@Data
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
    private Map<String, String> scenarioArchitectureMapping = new HashMap<>() {{
        put("DOCUMENT_GENERATION", "sequential-pipeline");
        put("MARKET_ANALYSIS", "parallel-analysis");
        put("INVESTMENT_DECISION", "debate-system");
        put("CUSTOMER_SERVICE", "smart-routing");
        put("IMAGE_ANALYSIS", "sequential-pipeline");
        put("SPEECH_RECOGNITION", "sequential-pipeline");
        put("VIDEO_ANALYSIS", "sequential-pipeline");
    }};
    
    /**
     * 复杂度到模型的默认映射
     */
    private Map<String, String> complexityModelMapping = Map.of(
        "HIGH", "qwen-max",
        "MEDIUM", "qwen-plus",
        "LOW", "qwen-turbo"
    );
}