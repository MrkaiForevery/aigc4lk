package com.air.platform.common.a2a.enums;

/**
 * A2A协议消息类型
 */
public enum A2AMessageType {
    // 基础通信
    DIRECT_REQUEST,
    DIRECT_RESPONSE,
    BROADCAST,
    HEARTBEAT,
    
    // 任务协作
    TASK_DELEGATION,
    TASK_RESULT,
    TASK_PROGRESS,
    
    // 能力管理
    CAPABILITY_QUERY,
    CAPABILITY_RESPONSE,
    AGENT_DISCOVERY,
    
    // 上下文同步
    CONTEXT_UPDATE,
    MEMORY_SYNC,
    KNOWLEDGE_QUERY,
    
    // 协商与辩论
    OPINION_SHARE,
    REBUTTAL,
    CONSENSUS_CHECK,
    CONSENSUS_RESULT,
    
    // 多模态协作
    MULTIMODAL_INPUT,
    MULTIMODAL_OUTPUT,
    MODALITY_CONVERSION,
    
    // 异常处理
    ERROR_NOTIFICATION,
    AGENT_STATUS_CHANGE,
    INTERVENTION_REQUEST
}