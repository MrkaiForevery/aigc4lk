package com.air.platform.common.model;

import com.air.platform.common.enums.ModelType;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * LLM模型定义
 */
@Data
@Builder
public class ModelDefinition {
    private String modelId;
    private ModelType type;
    private String provider;
    private String apiKey;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private List<String> capabilities;
    private Integer weight;
    private Boolean enabled;
    private Map<String, String> tags;
    
    // OpenAI兼容模式专用
    private String baseUrl;

    public boolean isEnabled() {
        return this.enabled;
    }
}

