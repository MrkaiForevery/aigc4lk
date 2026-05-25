package com.air.platform.common.model;

import com.air.platform.common.enums.ModelType;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 视频模型定义
 */
@Data
@Builder
public class VideoModelDefinition {
    private String modelId;
    private ModelType type;
    private String provider;
    private String apiKey;
    private String modelName;
    private Integer maxDurationSeconds;
    private List<String> supportedFormats;
    private List<String> features;
    private List<String> capabilities;
    private Integer weight;
    private Boolean enabled;
    
    // 视频生成专用
    private Integer maxResolution;
    private Integer fps;
}