package com.air.platform.common.multimodal.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MultimodalOutput {
    private String text;
    private String generatedImage;
    private String generatedAudio;
    private String generatedVideo;
    private Map<String, Object> metadata;
}