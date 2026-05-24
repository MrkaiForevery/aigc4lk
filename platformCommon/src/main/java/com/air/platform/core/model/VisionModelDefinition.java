package com.air.platform.core.model;

import com.air.platform.enums.ModelType;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 视觉模型定义
 */
@Data
@Builder
public class VisionModelDefinition {
    private String modelId;
    private ModelType type;
    private String provider;
    private String apiKey;
    private String modelName;
    private Integer maxImageSize;
    private List<String> supportedFormats;
    private List<String> features;
    private List<String> capabilities;
    private Integer weight;
    private Boolean enabled;
    
    // 图像生成专用
    private List<String> styles;
    private String endpoint;  // 自托管端点
}