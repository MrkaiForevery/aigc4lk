package com.air.platform.core.agent;

import com.air.platform.enums.ModalityType;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class BaseAgent implements AgentLifecycle {
    
    @Getter
    protected final String agentId;
    
    @Getter
    protected final String agentType;
    
    @Getter
    protected final List<String> capabilities;
    
    @Getter
    protected final List<ModalityType> supportedModalities;
    
    @Getter
    @Setter
    protected AgentStatus status = AgentStatus.INITIALIZING;
    
    protected final Map<String, Object> state = new ConcurrentHashMap<>();
    
    protected BaseAgent(Builder<?, ?> builder) {
        this.agentId = builder.agentId;
        this.agentType = builder.agentType;
        this.capabilities = builder.capabilities;
        this.supportedModalities = builder.supportedModalities;
    }
    
    public abstract Map<String, Object> execute(Map<String, Object> input);
    
    // Agent生命周期方法
    @Override
    public void onRegister(NacosRegistration registration) {
        this.status = AgentStatus.REGISTERED;
        log.info("Agent {} registered with Nacos", agentId);
    }
    
    @Override
    public void onModelBind(Object model) {
        state.put("boundModel", model);
        log.info("Agent {} bound to model: {}", agentId, model.getClass().getSimpleName());
    }
    
    @Override
    public void onDeregister() {
        this.status = AgentStatus.DEREGISTERED;
        log.info("Agent {} deregistered", agentId);
    }
    
    @Override
    public void onError(Throwable error) {
        this.status = AgentStatus.ERROR;
        log.error("Agent {} error", agentId, error);
    }
    
    @Override
    public void onRecover(String checkpointId) {
        this.status = AgentStatus.RECOVERED;
        log.info("Agent {} recovered from checkpoint: {}", agentId, checkpointId);
    }
    
    public static abstract class Builder<T extends BaseAgent, B extends Builder<T, B>> {
        protected String agentId;
        protected String agentType;
        protected List<String> capabilities;
        protected List<ModalityType> supportedModalities;
        
        public B agentId(String agentId) {
            this.agentId = agentId;
            return self();
        }
        
        public B agentType(String agentType) {
            this.agentType = agentType;
            return self();
        }
        
        public B capabilities(List<String> capabilities) {
            this.capabilities = capabilities;
            return self();
        }
        
        public B supportedModalities(List<ModalityType> supportedModalities) {
            this.supportedModalities = supportedModalities;
            return self();
        }
        
        protected abstract B self();
        public abstract T build();
    }
    
    public enum AgentStatus {
        INITIALIZING, REGISTERED, READY, BUSY, ERROR, DEREGISTERED, RECOVERED
    }
}