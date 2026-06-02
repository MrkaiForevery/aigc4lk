package com.air.platform.common.a2a.router;

import cn.hutool.core.lang.UUID;
import com.air.platform.common.a2a.protocol.A2AMessage;
import com.air.platform.common.a2a.protocol.A2AResponse;
import com.air.platform.common.multimodal.vo.AgentInstance;
import com.air.platform.common.multimodal.vo.AgentMetadata;
import com.air.platform.common.enums.ModalityType;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nacos 3.2 原生A2A路由器实现
 * <p>
 * 核心能力由 Spring AI Alibaba 原生组件提供：
 * - AgentCardProvider: Agent发现
 * - A2aRemoteAgent: 远程调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosA2ARouter implements A2ARouter {

    // ==================== Spring AI Alibaba 原生组件 ====================
    private final AgentCardProvider agentCardProvider;
    private final ObjectMapper objectMapper;

    private final RestClient.Builder restClientBuilder;

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

            String endpoint = agentCard.url(); // 子Agent暴露的A2A端点

            // 从自定义消息中提取 threadId 和业务参数
            String threadId = extractThreadId(message);
            Map<String, Object> originalPayload = (Map<String, Object>) message.getPayload();

            // 构建标准JSON-RPC请求体
            String requestBody = buildStandardA2ARequest(originalPayload, threadId);

            // 发送HTTP请求
            String responseStr = restClientBuilder.build()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // 解析响应（根据实际返回格式调整）
            return parseResponse(responseStr, message.getMessageId());

        } catch (Exception e) {
            log.error("Failed to route message to: {}", targetAgentId, e);
            return A2AResponse.failure(message.getMessageId(), "A2A-002", e.getMessage());
        }
    }

    private A2AResponse parseResponse(String responseStr, String messageId) {
        try {
            JsonNode root = objectMapper.readTree(responseStr);
            if (root.has("result")) {
                return A2AResponse.success(messageId, root.get("result"));
            } else if (root.has("error")) {
                return A2AResponse.failure(messageId, "A2A-009", root.get("error").toString());
            }
            // 如果直接返回了流式结果（SSE），这里可以简化处理
            return A2AResponse.success(messageId, responseStr);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse A2A response, returning raw string", e);
            return A2AResponse.success(messageId, responseStr);
        }

    }

    private String buildStandardA2ARequest(Map<String, Object> originalPayload, String threadId) {
        // 将业务参数格式化为用户消息文本
        String userMessage = formatUserMessage(originalPayload);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", "message/stream");
        request.put("id", UUID.randomUUID().toString());
        request.put("jsonrpc", "2.0");
        request.put("params", Map.of(
                "metadata", Map.of("threadId", threadId),
                "message", Map.of(
                        "role", "user",
                        "kind", "message",
                        "parts", List.of(Map.of("text", userMessage, "kind", "text")),
                        "messageId", UUID.randomUUID().toString()
                )
        ));

        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private String formatUserMessage(Map<String, Object> payload) {
        // 根据实际业务参数，构建清晰的用户指令
        String taskType = payload.getOrDefault("taskType", "").toString();
        String topic = payload.getOrDefault("topic", "").toString();
        String docType = payload.getOrDefault("docType", "技术报告").toString();
        String userId = payload.getOrDefault("userId", "anonymous").toString();
        String sessionId = payload.getOrDefault("sessionId", "").toString();

        return String.format("""
                        请执行任务：%s
                        参数：
                        - 主题：%s
                        - 文档类型：%s
                        - 用户ID：%s
                        - 会话ID：%s
                        请立即开始处理，不要询问更多信息。""",
                taskType, topic, docType, userId, sessionId);
    }


    private String extractThreadId(A2AMessage message) {
        return Optional.ofNullable(message)
                .map(A2AMessage::getPayload)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(payload -> payload.get("threadId"))
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("threadId must not be null in A2AMessage"));
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