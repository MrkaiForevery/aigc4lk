package com.air.platform.core.model;

import com.air.platform.enums.ModelType;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 语音模型定义
 */
@Data
@Builder
public class SpeechModelDefinition {
    private String modelId;
    private ModelType type;
    private String provider;
    private String apiKey;
    private String modelName;
    private List<String> languages;
    private Integer sampleRate;
    private List<String> channels;
    private Integer maxDurationSeconds;
    private List<String> features;
    private List<String> capabilities;
    private Integer weight;
    private Boolean enabled;
    
    // TTS专用
    private List<String> voices;
    private List<String> formats;
}