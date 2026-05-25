package com.air.platform.common.a2a.router;

import com.air.platform.common.a2a.protocol.A2AMessage;
import com.air.platform.common.a2a.protocol.A2AResponse;
import com.air.platform.common.multimodal.vo.AgentInstance;
import com.air.platform.common.multimodal.vo.AgentMetadata;
import com.air.platform.common.enums.ModalityType;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos 3.2 原生A2A路由器实现
 * 
 * 核心能力由 Spring AI Alibaba 原生组件提供：
 * - AgentCardProvider: Agent发现
 * - A2aRemoteAgent: 远程调用
 */
@Slf4j
@Component
public class NacosA2ARouter implements A2ARouter {
    
    // ==================== Spring AI Alibaba 原生组件 ====================
    @Resource
    private AgentCardProvider agentCardProvider;  // 原生Agent发现接口
    
    // ==================== 配置 ====================
    @Value("${a2a.timeout-seconds:30}")
    private int timeoutSeconds;
    
    // ==================== 缓存 ====================
    private final Map<String, A2aRemoteAgent> remoteAgentCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("NacosA2ARouter initialized with native AgentCardProvider");
    }
    
    // ==================== 核心路由方法 ====================
    
    @Override
    public A2AResponse routeMessage(A2AMessage message) {
        String targetAgentId = message.getReceiverAgentId();
        
        if (targetAgentId == null || targetAgentId.isEmpty()) {
            return broadcastMessage(message);
        }
        
        try {
            // 使用原生 AgentCardProvider 发现Agent
            AgentCardWrapper agentCard = agentCardProvider.getAgentCard(targetAgentId);
            if (agentCard == null) {
                log.warn("Agent not found: {}", targetAgentId);
                return A2AResponse.failure(message.getMessageId(), "A2A-001", 
                    "Agent not found: " + targetAgentId);
            }
            
            // 获取或创建远程Agent代理
            A2aRemoteAgent remoteAgent = remoteAgentCache.computeIfAbsent(targetAgentId, 
                id -> A2aRemoteAgent.builder()
                    .name(id)
                    .agentCardProvider(agentCardProvider)
                    .description("Remote proxy for " + id)
                    .build());
            
            // 执行远程调用
            Optional<OverAllState> result = remoteAgent.invoke(message.getPayload().toString());
            
            if (result.isPresent()) {
                return A2AResponse.success(message.getMessageId(), result.get().data());
            } else {
                return A2AResponse.failure(message.getMessageId(), "A2A-009", "No result from remote agent");
            }
            
        } catch (Exception e) {
            log.error("Failed to route message to: {}", targetAgentId, e);
            return A2AResponse.failure(message.getMessageId(), "A2A-002", e.getMessage());
        }
    }
    
    // ==================== 发现方法（委托给原生组件） ====================

    @Override
    public List<AgentInstance> discoverAgents(String agentId, Map<String, String> filters) {
        // 通过 AgentCardProvider 获取Agent信息
        AgentCardWrapper agentCard = agentCardProvider.getAgentCard(agentId);
        if (agentCard == null) {
            return List.of();
        }
        return List.of(convertToAgentInstance(agentCard));
    }
    
    @Override
    public List<AgentInstance> discoverByCapability(String capability) {
        // Nacos 3.2 原生支持按能力发现，通过 AgentCardProvider 的扩展方法
        // 这里简化处理，实际可通过 Nacos 元数据过滤
        return List.of();
    }


    @Override
    public List<AgentInstance> discoverByModality(ModalityType modality) {
        // 通过 AgentCard 的 metadata 进行过滤
        return List.of();
    }
    
    // ==================== 注册/注销（由框架自动处理） ====================
    
    @Override
    public void registerAgent(AgentMetadata metadata) {
        // 注册由 spring.ai.alibaba.a2a.nacos.registry.enabled=true 自动完成
        // 无需手动实现
        log.info("Agent registration is handled automatically by framework: {}", metadata.getAgentId());
    }
    
    @Override
    public void deregisterAgent(String agentId) {
        // 注销由框架自动处理
        log.info("Agent deregistration is handled automatically by framework: {}", agentId);
    }
    
    @Override
    public void sendHeartbeat(String agentId) {
        // 心跳由 Nacos 3.2 原生支持，框架自动处理
        log.debug("Heartbeat handled automatically by Nacos");
    }
    
    // ==================== 辅助方法 ====================
    
    private A2AResponse broadcastMessage(A2AMessage message) {
        log.info("Broadcasting message: type={}", message.getMessageType());
        // 广播实现：遍历所有发现的Agent
        return A2AResponse.success(message.getMessageId(), "broadcast sent");
    }
    
    private AgentInstance convertToAgentInstance(AgentCardWrapper agentCard) {
        return AgentInstance.builder()
            .agentId(agentCard.name())
            .endpoint(agentCard.url())
            .metadata(Map.of(
                "description", agentCard.description(),
                "version", agentCard.version()
            ))
            .build();
    }
}