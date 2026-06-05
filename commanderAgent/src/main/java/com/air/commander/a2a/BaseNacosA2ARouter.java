package com.air.commander.a2a;

import cn.hutool.core.util.ObjectUtil;
import com.air.commander.model.*;
import com.air.commander.resilience.ResilienceManager;
import com.alibaba.cloud.ai.a2a.registry.nacos.discovery.NacosAgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.http.A2AHttpResponse;
import io.a2a.http.JdkA2AHttpClient;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 基于nacos的原生A2A路由执行器实现
 * 核心:打通动态A2A路由
 */
@Slf4j
@Component
public class BaseNacosA2ARouter {

    private final DiscoveryClient discoveryClient;
    private final NacosAgentCardProvider agentCardProvider;
    private final ResilienceManager resilience;
    private final ObjectMapper objectMapper;
    private final JdkA2AHttpClient httpClient;   // 单例

    @Autowired
    public BaseNacosA2ARouter(DiscoveryClient discoveryClient,
                              NacosAgentCardProvider nacosAgentCardProvider,
                              ResilienceManager resilience,
                              ObjectMapper objectMapper) {   // 注入 Spring 管理的 ObjectMapper
        this.discoveryClient = discoveryClient;
        this.agentCardProvider = nacosAgentCardProvider;
        this.resilience = resilience;
        // 若Spring没有提供ObjectMapper，则使用自定义配置；否则用注入的。
        this.objectMapper = objectMapper != null ? objectMapper : createObjectMapper();
        this.httpClient = new JdkA2AHttpClient();   // 整个Router共用一个HTTP客户端
    }

    // 当Spring容器中没有ObjectMapper时，创建一个带有基本配置的实例
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // 可添加其他必要的配置
        return mapper;
    }

    // ==================== Agent 发现 ====================

    /**
     * 获取所有在线 Agent 的名称（用于动态编排 Prompt）
     */
    public Set<String> getAvailableAgents() {
        return getAgentCards().stream()
                .map(AgentCardWrapper::name)
                .collect(Collectors.toSet());
    }

    /**
     * 获取所有在线 Agent 的完整卡片信息（包含描述和技能）
     */
    public List<AgentCardWrapper> getAgentCards() {
        List<AgentCardWrapper> cards = new ArrayList<>();
        List<String> services = discoveryClient.getServices();
        for (String serviceId : services) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            if (instances.isEmpty()) continue;
            // 取第一个实例的元数据构建 AgentCard
            ServiceInstance instance = instances.get(0);
            Map<String, String> metadata = instance.getMetadata();
            String agentName = metadata.get("agent-name");
            if (ObjectUtil.isNotEmpty(agentName)) {
                AgentCardWrapper agentCard = agentCardProvider.getAgentCard(agentName);
                if (agentCard != null) {   // 防御性检查，防止NPE
                    cards.add(agentCard);
                }
            }
        }
        return cards;
    }

    // ==================== Agent 调用 ====================

    /**
     * 调用指定的 Agent
     */
    public ExecutionResult callAgent(Step step,
                                     Map<String, Object> context,
                                     Map<String, String> tokens,
                                     String threadId,
                                     String xid,
                                     MemoryContext memoryCtx) {
        String agentName = step.getAgent();
        TextPart textPart = new TextPart(buildAgentContent(step, context));

        // 1. 构建 A2A 标准消息，将记忆上下文全部放入 metadata
        Message userMessage = new Message.Builder()
                .role(Message.Role.USER)
                .parts(List.of(textPart))
                .metadata(Map.of(
                        "threadId", threadId,
                        "xid", xid != null ? xid : "",
                        "stepId", step.getId(),
                        "task", step.getTask(),
                        "credentialTokens", tokens != null ? tokens : Map.of(),
                        "recentMessages", memoryCtx.getRecentMessages() != null ?
                                memoryCtx.getRecentMessages() : List.of(),
                        "userProfile", memoryCtx.getUserProfile() != null ?
                                memoryCtx.getUserProfile() : Map.of(),
                        "preferences", memoryCtx.getPreferences() != null ?
                                memoryCtx.getPreferences() : Map.of()
                ))
                .build();

        // 2. 通过 DiscoveryClient 获取 Agent 实例地址
        List<ServiceInstance> instances = discoveryClient.getInstances(agentName);
        if (instances.isEmpty()) {
            return ExecutionResult.builder()
                    .stepId(step.getId())
                    .success(false)
                    .error("Agent not found: " + agentName)
                    .build();
        }

        // 简单的随机负载均衡
        ServiceInstance instance = instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
        String url = instance.getUri() + "/api/a2a/message";

        // 3. 带弹性保护的 HTTP 调用
        return resilience.executeWithFullProtection(
                "agent-" + agentName,
                () -> {
                    String requestBody;
                    try {
                        requestBody = objectMapper.writeValueAsString(userMessage);
                    } catch (JsonProcessingException e) {
                        log.error("A2A请求序列化失败: agent={}, stepId={}", agentName, step.getId(), e);
                        throw new RuntimeException("A2A请求序列化失败", e);
                    }

                    // 使用单例 httpClient
                    A2AHttpResponse a2AHttpResponse;
                    try {
                        a2AHttpResponse = httpClient.createPost()
                                .url(url)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("TX_XID", xid != null ? xid : "")
                                .body(requestBody)
                                .post();
                    } catch (IOException e) {
                        log.error("A2A IO异常: agent={}, url={}", agentName, url, e);
                        throw new RuntimeException("A2A请求IO异常", e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();  // 恢复中断标志
                        log.error("A2A请求被中断: agent={}", agentName, e);
                        throw new RuntimeException("A2A请求被中断", e);
                    }

                    if (a2AHttpResponse.success()) {
                        LkA2AResponse lkA2AResponse;
                        try {
                            lkA2AResponse = objectMapper.readValue(a2AHttpResponse.body(), LkA2AResponse.class);
                        } catch (JsonProcessingException e) {
                            log.error("A2A响应反序列化失败: agent={}, body={}", agentName, a2AHttpResponse.body(), e);
                            throw new RuntimeException("A2A响应反序列化失败", e);
                        }
                        return convertResponse(lkA2AResponse, step.getId());
                    } else {
                        log.warn("Agent返回非成功状态码: agent={}, status={}", agentName, a2AHttpResponse.status());
                        return ExecutionResult.builder()
                                .stepId(step.getId())
                                .success(false)
                                .error("Agent HTTP error: " + a2AHttpResponse.status())
                                .build();
                    }
                },
                () -> {
                    log.warn("A2A调用降级: agent={}, stepId={}", agentName, step.getId());
                    return ExecutionResult.builder()
                            .stepId(step.getId())
                            .success(false)
                            .error("Agent调用降级")
                            .build();
                }
        );
    }

    // ==================== 结果转换 ====================

    /**
     * 将 A2aResponse 转换为内部 ExecutionResult
     *
     * @param response A2A 调用响应
     * @param stepId   当前步骤 ID
     * @return 统一执行结果
     */
    @SuppressWarnings("unchecked")
    private ExecutionResult convertResponse(LkA2AResponse response, String stepId) {
        // 1. 处理 A2A 中断命令
        if (response.getCommand() != null) {
            Map<String, Object> cmdMap = objectMapper.convertValue(response.getCommand(), Map.class);

            String type = (String) cmdMap.getOrDefault("type", "UNKNOWN");
            String scope = (String) cmdMap.getOrDefault("scope", "");
            String message = (String) cmdMap.getOrDefault("message", "");
            List<String> scopes = (List<String>) cmdMap.getOrDefault("requiredScopes", List.of());

            return ExecutionResult.builder()
                    .stepId(stepId)
                    .success(false)
                    .command(ExecutionResult.Command.builder()
                            .type(type)
                            .scope(scope)
                            .message(message)
                            .requiredScopes(scopes)
                            .build())
                    .durationMs(extractDuration(response))
                    .build();
        }

        // 2. 提取 Agent 回复的文本内容
        Map<String, Object> output = new HashMap<>();
        if (response.getMessages() != null) {
            response.getMessages().stream()
                    .filter(m -> m.getRole() == Message.Role.AGENT)
                    .findFirst()
                    .ifPresent(msg -> {
                        String text = msg.getParts().stream()
                                .filter(p -> p instanceof TextPart)
                                .map(p -> ((TextPart) p).getText())
                                .findFirst()
                                .orElse("");
                        output.put("content", text);
                    });
        }

        return ExecutionResult.builder()
                .stepId(stepId)
                .success(true)
                .output(output)
                .durationMs(extractDuration(response))
                .build();
    }

    private long extractDuration(LkA2AResponse response) {
        if (response.getMetadata() != null && response.getMetadata().containsKey("durationMs")) {
            Object val = response.getMetadata().get("durationMs");
            return val instanceof Number ? ((Number) val).longValue() : 0L;
        }
        return 0L;
    }

    // ==================== 辅助方法 ====================

    private String buildAgentContent(Step step, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(step.getTask()).append("\n");
        if (step.getInput() != null && !step.getInput().isEmpty()) {
            sb.append("Input: ").append(resolveInput(step.getInput(), context));
        }
        return sb.toString();
    }

    private Map<String, Object> resolveInput(Map<String, Object> input, Map<String, Object> context) {
        if (input == null) return Map.of();
        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getValue() instanceof String str && str.matches("\\{.*\\.output\\}")) {
                String refKey = str.replace("{", "").replace("}", "");
                resolved.put(entry.getKey(), context.getOrDefault(refKey, str));
            } else {
                resolved.put(entry.getKey(), entry.getValue());
            }
        }
        return resolved;
    }
}