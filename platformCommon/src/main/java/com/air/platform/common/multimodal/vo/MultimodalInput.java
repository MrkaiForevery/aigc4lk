package com.air.platform.common.multimodal.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MultimodalInput {
    private String text;
    private List<String> images;
    private List<String> audios;
    private List<String> videos;
    private Map<String, Object> metadata;
}








