package com.air.platform.common.multimodal.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AgentInstance {
    private String agentId;
    private String endpoint;
    private Map<String, String> metadata;
    
    public String getAgentId() {
        return agentId;
    }
}