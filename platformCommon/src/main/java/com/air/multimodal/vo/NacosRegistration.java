package com.air.multimodal.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NacosRegistration {
    private String agentId;
    private String endpoint;
    private Map<String, String> metadata;
}
