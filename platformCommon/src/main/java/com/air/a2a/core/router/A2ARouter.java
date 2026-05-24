package com.air.a2a.core.router;

import com.air.a2a.core.protocol.A2AMessage;
import com.air.a2a.core.protocol.A2AResponse;
import com.air.multimodal.vo.AgentInstance;
import com.air.multimodal.vo.AgentMetadata;
import com.air.platform.enums.ModalityType;

import java.util.List;
import java.util.Map;

/**
 * A2A路由器接口
 * 基于 Nacos 3.2 原生 A2A 能力实现
 */
public interface A2ARouter {
    
    /**
     * 路由A2A消息
     * @param message A2A消息
     * @return 响应
     */
    A2AResponse routeMessage(A2AMessage message);
    
    /**
     * 发现Agent实例
     * @param agentId Agent ID（支持模糊匹配）
     * @param filters 过滤条件（如 modality、capability）
     * @return Agent实例列表
     */
    List<AgentInstance> discoverAgents(String agentId, Map<String, String> filters);
    
    /**
     * 根据能力发现Agent
     * @param capability 能力名称（如 OCR、SPEECH_TO_TEXT）
     * @return Agent实例列表
     */
    List<AgentInstance> discoverByCapability(String capability);
    
    /**
     * 根据模态发现Agent
     * @param modality 模态类型（TEXT/VISION/SPEECH/VIDEO）
     * @return Agent实例列表
     */
    List<AgentInstance> discoverByModality(ModalityType modality);
    
    /**
     * 注册Agent到Nacos
     * @param metadata Agent元数据
     */
    void registerAgent(AgentMetadata metadata);
    
    /**
     * 从Nacos注销Agent
     * @param agentId Agent ID
     */
    void deregisterAgent(String agentId);
    
    /**
     * 发送心跳
     * @param agentId Agent ID
     */
    void sendHeartbeat(String agentId);
}