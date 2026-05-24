package com.air.platform.core.architecture;

import com.air.platform.core.model.ScenarioBinding;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ArchitectureRegistry {
    
    private final Map<String, AgentArchitecture> architectures = new ConcurrentHashMap<>();
    private final Map<String, ScenarioBinding> scenarioBindings = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("ArchitectureRegistry initialized");
    }
    
    public void registerArchitecture(AgentArchitecture architecture) {
        architectures.put(architecture.getId(), architecture);
        log.info("Registered architecture: {} ({})", 
            architecture.getId(), architecture.getName());
    }
    
    public AgentArchitecture getArchitecture(String architectureId) {
        AgentArchitecture architecture = architectures.get(architectureId);
        if (architecture == null) {
            throw new IllegalArgumentException("Architecture not found: " + architectureId);
        }
        return architecture;
    }
    
    public void registerScenarioBinding(ScenarioBinding binding) {
        scenarioBindings.put(binding.getScenario(), binding);
        log.info("Registered scenario binding: {}", binding.getScenario());
    }
    
    public ScenarioBinding getScenarioBinding(String scenario) {
        return scenarioBindings.get(scenario);
    }
    
    public Map<String, AgentArchitecture> getAllArchitectures() {
        return new ConcurrentHashMap<>(architectures);
    }
    
    public boolean containsArchitecture(String architectureId) {
        return architectures.containsKey(architectureId);
    }
    
    public void removeArchitecture(String architectureId) {
        architectures.remove(architectureId);
        log.info("Removed architecture: {}", architectureId);
    }
}