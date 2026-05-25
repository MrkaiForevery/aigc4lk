package com.air.platform.common.multimodal.vo;

import com.air.platform.common.enums.ModalityType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentMetadata {
    private String agentId;
    private String agentType;
    private List<String> capabilities;
    private List<ModalityType> supportedModalities;
    private String a2aVersion;
    private String endpoint;
    private String healthCheckEndpoint;
}