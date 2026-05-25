package com.air.platform.common.architecture;

import com.air.platform.common.enums.ArchitectureType;

import java.util.Map;

public interface AgentArchitecture {
    
    String getId();
    
    String getName();
    
    AgentArchitecture withModel(Object model);
    
    AgentArchitecture withTools(Object toolProvider);
    
    Map<String, Object> execute(Map<String, Object> input);
    
    Map<String, Object> executeStream(Map<String, Object> input);
    
    ArchitectureType getType();
    
    default AgentArchitecture withConfig(Map<String, Object> config) {
        return this;
    }
}
