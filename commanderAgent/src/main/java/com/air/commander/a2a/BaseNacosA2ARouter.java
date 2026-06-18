package com.air.commander.a2a;

import cn.hutool.core.util.ObjectUtil;
import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.model.*;
import com.air.commander.resilience.ResilienceManager;
import com.alibaba.cloud.ai.a2a.registry.nacos.discovery.NacosAgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于nacos的原生A2A路由执行器实现
 * 核心功能:
 * 1.实时获取所有基于nacos注册的A2A的子agent的agentCard信息列表
 * --->用于commanderAgent中使用LLM动态构建执行计划动作的动态提示词，告诉LLM我现有的子agent的能力有哪些
 * 2.可根据输入agentName找到目标A2A-agent的endPoint
 * --->并能够进行动态路由调用子agent提供的服务
 */
@Slf4j
@Component
public class BaseNacosA2ARouter {

    private static final String IS_RELATION_AGENT = "is_relation_agent";
    private static final String RELATION_AGENT_NAME = "relation_agent_Name";
    private static final String A2A_CALL = "a2a-call";
    private static final String TX_XID = "TX_XID";
    private static final String JSON_RPC = "jsonrpc";
    private static final String METHOD = "method";
    private static final String PARAMS = "params";
    private static final String ID = "id";

    private final DiscoveryClient discoveryClient;
    private final NacosAgentCardProvider agentCardProvider;
    private final ResilienceManager resilience;
    private final ObjectMapper objectMapper;
    private final RestClient restClient; // 单例
    private final PromptManagerBuilder promptManagerBuilder;

    @Autowired
    public BaseNacosA2ARouter(DiscoveryClient discoveryClient,
                              NacosAgentCardProvider nacosAgentCardProvider,
                              ResilienceManager resilience,
                              ObjectMapper objectMapper,
                              RestClient.Builder restClientBuilder,
                              PromptManagerBuilder promptManagerBuilder) {   // 注入 Spring 管理的 ObjectMapper
        this.discoveryClient = discoveryClient;
        this.agentCardProvider = nacosAgentCardProvider;
        this.resilience = resilience;
        // 若Spring没有提供ObjectMapper，则使用自定义配置；否则用注入的。
        this.objectMapper = objectMapper;
        // 构建并复用RestClient实例
        this.restClient = restClientBuilder.build();
        this.promptManagerBuilder = promptManagerBuilder;
        ;
    }

    // ==================== Agent 发现 ====================

    /**
     * 获取所有在线 Agent 的名称（用于动态编排 Prompt）
     */
    public Set<AgentCardWrapper> getAvailableAgents() {
        return getAgentCards().stream()
                .collect(Collectors.toSet());
    }

    /**
     * 获取所有在线 Agent 的完整卡片信息
     * 这里是通过serverInstance的meta里面的自定义标识，感知serverInstance是否关联了agent endpoint。
     */
    private List<AgentCardWrapper> getAgentCards() {
        return discoveryClient.getServices().stream()
                .map(serviceId -> discoveryClient.getInstances(serviceId))
                .filter(instances -> !instances.isEmpty())
                .map(instances -> instances.get(0).getMetadata())  // 只取第一个实例的元数据
                .filter(meta -> meta != null && Boolean.parseBoolean(meta.getOrDefault(IS_RELATION_AGENT, "false")))
                .map(meta -> meta.get(RELATION_AGENT_NAME))
                .filter(Objects::nonNull)
                .distinct()
                .map(agentCardProvider::getAgentCard)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== Agent 调用 ====================

    /**
     * 调用指定的 Agent
     */
    public ExecutionResult callAgent(Step step,
                                     Map<String, String> tokens,
                                     String threadId,
                                     String xid,
                                     MemoryContext memoryCtx) {
        log.debug("开始调用远程agent:{}..... ", step.getAgent());
        long start = System.currentTimeMillis();

        String agentName = step.getAgent();
        if (agentName == null || agentName.isBlank()) {
            return ExecutionResult.builder()
                    .relationPlanId(step.getRelationPlanId())
                    .stepId(step.getId())
                    .success(false)
                    .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                    .error("[BaseNacosA2ARouter.callAgent]A2A Agent name is empty")
                    .build();
        }
        //提取提示词
        String preBuiltA2AAgentContent = step.getPreBuiltA2AAgentContent();
        if (ObjectUtil.isEmpty(preBuiltA2AAgentContent)) {
            throw new RuntimeException("[A2A-error]:step对象中提取提示词prompt失败，提示词不能为空");
        }

        TextPart textPart = new TextPart(preBuiltA2AAgentContent);

        // 构建 A2A 标准消息
        Message userMessage = buildMessage(step, tokens, threadId, xid, memoryCtx, textPart);

        // 获取 Agent 端点
        AgentCardWrapper agentCardWrapper = agentCardProvider.getAgentCard(agentName);
        if (ObjectUtil.isEmpty(agentCardWrapper)) {
            return ExecutionResult.builder()
                    .stepId(step.getId())
                    .success(false)
                    .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                    .error("Agent not found in A2A registry: " + agentName)
                    .build();
        }
        String endpoint = agentCardWrapper.url();
        if (endpoint == null) {
            return ExecutionResult.builder()
                    .stepId(step.getId())
                    .success(false)
                    .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                    .error("Agent not found in A2A registry: " + agentName)
                    .build();
        }

        // 带弹性保护的调用
        ExecutionResult executionResult = resilience.executeWithFullProtection(
                A2A_CALL,
                () -> {
                    try {
                        String requestBody = buildRpcRequest(userMessage);
                        // 发送 HTTP 请求
                        String response = restClient.post()
                                .uri(endpoint)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(TX_XID, xid != null ? xid : "")
                                .body(requestBody)
                                .exchange((request, thatResponse) -> {
                                    StringBuilder sb = new StringBuilder();
                                    try (BufferedReader reader = new BufferedReader(
                                            new InputStreamReader(thatResponse.getBody(), StandardCharsets.UTF_8))) {
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            sb.append(line).append("\n");
                                        }
                                        return sb.toString();
                                    } catch (IOException e) {
                                        throw new RuntimeException("读取响应流失败", e);
                                    }
                                });
                        // 解析响应体（可能是 SSE 流或纯 JSON）
                        return parseSseResponse(response, step.getRelationPlanId(), step.getId(), agentName);
                    } catch (Exception e) {
                        log.error("A2A调用失败: agent={}, endpoint={}", agentName, endpoint, e);
                        throw new RuntimeException("A2A请求失败", e);
                    }
                },
                () -> {
                    log.warn("A2A调用降级: agent={}, stepId={}", agentName, step.getId());
                    return ExecutionResult.builder()
                            .relationPlanId(step.getRelationPlanId())
                            .stepId(step.getId())
                            .success(false)
                            .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                            .output(Map.of("content", "Agent 调用暂时不可用，已跳过此步骤"))
                            .error("A2A Agent调用降级")
                            .build();
                }
        );

        long end = System.currentTimeMillis();
        log.debug("远程调用agent:{}结束，本次耗时:{}ms ", agentName, end - start);

        return executionResult;
    }

    /**
     * 构建发送给远程子agent的RpcRequest
     */
    private String buildRpcRequest(Message userMessage) throws JsonProcessingException {
        // 构建 JSON-RPC 请求
        Map<String, Object> rpcRequest = new LinkedHashMap<>();
        rpcRequest.put(JSON_RPC, "2.0");
        rpcRequest.put(METHOD, "message/stream");
        rpcRequest.put(PARAMS, Map.of("message", userMessage));
        rpcRequest.put(ID, UUID.randomUUID().toString());

        String requestBody = objectMapper.writeValueAsString(rpcRequest);
        return requestBody;
    }

    /**
     * 构建 A2A 标准消息
     */
    private static Message buildMessage(Step step, Map<String, String> tokens, String threadId, String xid, MemoryContext memoryCtx, TextPart textPart) {

        // 只传递必要的元信息，不传递记忆上下文
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("threadId", threadId);
        metadata.put("xid", xid != null ? xid : "");
        metadata.put("stepId", step.getId());
        metadata.put("credentialTokens", tokens != null ? tokens : Map.of());

        // 按需传递记忆上下文（仅当步骤声明需要时）
        if (step.isIncludeChatHistory() && memoryCtx.getRecentMessages() != null) {
            // 只取最近3条有效消息
            List<MemoryContext.Message> memoryContexts = memoryCtx.getRecentMessages().stream()
                    .filter(msg -> !"done".equals(msg.getContent()))
                    .skip(Math.max(0, memoryCtx.getRecentMessages().size() - 3))
                    .collect(Collectors.toList());
            metadata.put("recentMessages", memoryContexts);
        }

        Message userMessage = new Message.Builder()
                .role(Message.Role.USER)
                .parts(List.of(textPart))
                .metadata(metadata)
                .build();
        return userMessage;
    }

    /**
     * 解析 JSON-RPC 响应体（兼容 SSE 流和纯 JSON）
     */
    private ExecutionResult parseSseResponse(String responseBody, String planId, String stepId, String agentName) {
        try {
            // 快速判断响应类型
            if (responseBody == null || responseBody.isEmpty()) {
                return ExecutionResult.builder()
                        .relationPlanId(planId)
                        .stepId(stepId)
                        .success(false)
                        .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                        .error("Empty response body")
                        .build();
            }
            if (!responseBody.contains("data:") && !responseBody.trim().startsWith("{")) {
                return ExecutionResult.builder()
                        .relationPlanId(planId)
                        .stepId(stepId)
                        .success(false)
                        .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                        .error("Unexpected response format")
                        .build();
            }

            // 如果包含 "data:"，从后往前查找最后一条 artifact-update
            if (responseBody.contains("data:")) {
                String lastArtifactJson = findLastArtifact(responseBody);
                if (lastArtifactJson != null) {
                    return parseArtifactResult(lastArtifactJson,planId, stepId);
                } else {
                    return ExecutionResult.builder()
                            .relationPlanId(planId)
                            .stepId(stepId)
                            .success(false)
                            .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                            .error("No artifact-update found in SSE response")
                            .build();
                }
            } else {
                // 纯 JSON 响应（例如顶层 JSON-RPC 错误）
                Map<String, Object> rpcResponse = objectMapper.readValue(responseBody, Map.class);
                if (rpcResponse.containsKey("error") && rpcResponse.get("error") != null) {
                    Map<String, Object> error = (Map<String, Object>) rpcResponse.get("error");
                    log.warn("JSON-RPC error from agent {}: code={}, message={}",
                            agentName, error.get("code"), error.get("message"));
                    return ExecutionResult.builder()
                            .relationPlanId(planId)
                            .stepId(stepId)
                            .success(false)
                            .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                            .error("Agent JSON-RPC error: " + error.get("message"))
                            .build();
                }
                return ExecutionResult.builder()
                        .relationPlanId(planId)
                        .stepId(stepId)
                        .success(false)
                        .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                        .error("Unexpected JSON response")
                        .build();
            }
        } catch (Exception e) {
            log.error("解析 SSE 响应失败: agent={}", agentName, e);
            return ExecutionResult.builder()
                    .relationPlanId(planId)
                    .stepId(stepId)
                    .success(false)
                    .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                    .error("Failed to parse response: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 从后往前扫描，找到最后一条包含 "artifact-update" 的 JSON 行
     */
    private String findLastArtifact(String responseBody) {
        int endIndex = responseBody.length();
        int lineEnd = endIndex;

        while (lineEnd > 0) {
            // 找上一行结束位置
            int lineStart = responseBody.lastIndexOf('\n', lineEnd - 1);
            if (lineStart == -1) {
                lineStart = 0;
            } else {
                lineStart += 1; // 跳过 '\n'
            }

            String line = responseBody.substring(lineStart, lineEnd).trim();
            if (line.startsWith("data:")) {
                String json = line.substring(5).trim(); // 去掉 "data:"
                if (json.contains("\"kind\":\"artifact-update\"")) {
                    return json; // 找到最后一条，直接返回
                }
            }

            lineEnd = lineStart - 1; // 移到上一行末尾
            if (lineEnd < 0) break;
        }
        return null;
    }

    /**
     * 解析 artifact JSON，提取文本内容
     */
    private ExecutionResult parseArtifactResult(String json,String planId, String stepId) {
        try {
            Map<String, Object> rpcResponse = objectMapper.readValue(json, Map.class);
            Map<String, Object> result = (Map<String, Object>) rpcResponse.get("result");
            Map<String, Object> artifact = (Map<String, Object>) result.get("artifact");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) artifact.get("parts");
            String text = "";
            for (Map<String, Object> part : parts) {
                if ("text".equals(part.get("kind"))) {
                    text = (String) part.get("text");
                    break;
                }
            }
            return ExecutionResult.builder()
                    .relationPlanId(planId)
                    .stepId(stepId)
                    .success(true)
                    .executionStatus(ExecutionResult.ExecutionStatus.DONE)
                    .output(Map.of("content", text))
                    .build();
        } catch (Exception e) {
            log.error("解析 artifact 失败", e);
            return ExecutionResult.builder()
                    .relationPlanId(planId)
                    .stepId(stepId).success(false)
                    .executionStatus(ExecutionResult.ExecutionStatus.FAILURE)
                    .error("Failed to parse artifact: " + e.getMessage())
                    .build();
        }
    }
}