package com.air.platform.common.model;

import com.air.platform.common.enums.ArchitectureType;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 架构定义，todo 这里要解释清楚
 */
@Data
@Builder
public class ArchitectureDefinition {
    private String architectureId;
    private String name;
    private String description;
    private ArchitectureType type;
    private List<String> supportedScenarios;
    private List<String> requiredCapabilities;
    private Map<String, Object> config;
    private Boolean enabled;
}

