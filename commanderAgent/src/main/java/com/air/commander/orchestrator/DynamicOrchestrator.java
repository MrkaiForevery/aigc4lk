package com.air.commander.orchestrator;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.model.ValidationResult;
import com.air.commander.resilience.ResilienceManager;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态编排器
 * 用于执行步骤计划
 */
@Slf4j
@Component
public class DynamicOrchestrator {

    private final ConcurrentHashMap<String,ChatClient> chatClientMaps = new ConcurrentHashMap<>();
    private final BaseNacosA2ARouter baseNacosA2ARouter;
    private final PlanValidator planValidator;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;
    private final PromptManagerBuilder promptManagerBuilder;

    public DynamicOrchestrator(@Qualifier("reasoningModelClient") ChatClient reasoningModelClient,
                               @Qualifier("fastModelClient") ChatClient fastModelClient,
                               @Qualifier("plusModelClient") ChatClient plusModelClient,
                               BaseNacosA2ARouter baseNacosA2ARouter,
                               PlanValidator planValidator,
                               ResilienceManager resilienceManager,
                               PromptManagerBuilder promptManagerBuilder) {
        chatClientMaps.put("reasoningModelClient",reasoningModelClient);
        chatClientMaps.put("fastModelClient",fastModelClient);
        chatClientMaps.put("plusModelClient",plusModelClient);
        this.baseNacosA2ARouter = baseNacosA2ARouter;
        this.planValidator = planValidator;
        this.resilienceManager = resilienceManager;
        this.objectMapper = new ObjectMapper();
        this.promptManagerBuilder = promptManagerBuilder;
    }

    /**
     * 调用复杂大模型生成任务的执行计划，且带有生成计划结果的校验逻辑，如果校验失败则需要重新生成执行计划，最多校验2次
     */
    public OrchestrationPlan generatePlan(String userInput, MemoryContext memoryCtx, String choseChatClientBeanName) {
        // 1. 构建完整的 Prompt
        Set<AgentCardWrapper> availableAgents = baseNacosA2ARouter.getAvailableAgents();
        String prompt = promptManagerBuilder.buildDynamicOrchestratorGeneratePlanPrompt(
                userInput, memoryCtx, availableAgents);

        // 2. 首次尝试生成计划（带保护）
        OrchestrationPlan plan = tryBuildPlan(prompt,choseChatClientBeanName);

        // 3. 校验并重试
        ValidationResult vr = planValidator.validateOrchestrationPlan(plan);
        for (int retry = 0; retry < 2 && !vr.isValid(); retry++) {
            String errors = String.join("; ", vr.getErrors());
            String retryPrompt = prompt + "\n\n上次计划有误：" + errors + "\n请修正后重新生成。";
            plan = tryBuildPlan(retryPrompt, choseChatClientBeanName);  // 再次尝试
            vr = planValidator.validateOrchestrationPlan(plan);
        }

        // 4. 强制注入 userQuery（无论来源，确保每个步骤都有）
        injectUserQuery(plan, userInput);
        return plan;
    }

    /**
     * 调用 LLM 生成计划并解析，失败时返回降级计划
     */
    private OrchestrationPlan tryBuildPlan(String prompt, String choseChatClientBeanName) {
        ChatClient chatClient = this.chatClientMaps.get(choseChatClientBeanName);
        String llmOutput = resilienceManager.executeWithFullProtection(
                "llm-reasoning-model",
                () -> chatClient.prompt(prompt).call().content(),
                () -> "{\"steps\": [{\"id\":\"step1\",\"type\":\"LLM_CALL\",\"task\":\"ANSWER\"}]}"
        );
        try {
            Map<String, Object> planMap = objectMapper.readValue(llmOutput, Map.class);
            List<Step> steps = parseSteps(planMap);
            return OrchestrationPlan.builder()
                    .planId(UUID.randomUUID().toString())
                    .executionMode(detectMode(planMap))
                    .steps(steps)
                    .build();
        } catch (Exception e) {
            log.error("解析 LLM 生成的计划失败，返回降级计划", e);
            return buildFallbackPlan();
        }
    }

    /**
     * 注入 userQuery 到每个步骤的 input 中
     */
    private void injectUserQuery(OrchestrationPlan plan, String userInput) {
        for (Step step : plan.getSteps()) {
            if (step.getInput() == null) {
                step.setInput(new HashMap<>());
            }
            step.getInput().put("userQuery", userInput);
        }
    }

    /**
     * 降级计划：单步 LLM_CALL
     */
    private OrchestrationPlan buildFallbackPlan() {
        Step fallbackStep = Step.builder()
                .id("step1")
                .type(Step.StepType.LLM_CALL)
                .task("直接回答用户问题")
                .build();
        return OrchestrationPlan.builder()
                .planId(UUID.randomUUID().toString())
                .executionMode(OrchestrationPlan.ExecutionMode.SEQUENTIAL)
                .steps(List.of(fallbackStep))
                .build();
    }


    /**
     * 解析 LLM 返回的步骤列表
     */
    private List<Step> parseSteps(Map<String, Object> planMap) {
        List<Map<String, Object>> stepList = getList(planMap, "steps");
        if (stepList == null || stepList.isEmpty()) {
            return List.of();
        }
        return stepList.stream()
                .map(this::parseStep)
                .collect(Collectors.toList());
    }

    /**
     * 解析单个步骤
     */
    /**
     * 解析单个步骤
     */
    private Step parseStep(Map<String, Object> stepMap) {
        Step.StepBuilder builder = Step.builder()
                .id(getString(stepMap, "id"))
                .type(Step.StepType.valueOf(getString(stepMap, "type")))
                .agent(getString(stepMap, "agent"))
                .task(getString(stepMap, "task"))
                .input(getMap(stepMap, "input"))
                .dependsOn(getStringList(stepMap, "dependsOn"))
                .mandatory(Boolean.TRUE.equals(stepMap.get("mandatory")))
                .includeChatHistory(Boolean.TRUE.equals(stepMap.get("includeChatHistory")));

        // 解析 checkpoint 配置（已有逻辑）
        parseCheckpoint(stepMap).ifPresent(builder::checkpoint);

        // 解析 conditionConfig 配置（新增逻辑）
        parseConditionConfig(stepMap).ifPresent(builder::conditionConfig);

        return builder.build();
    }

    /**
     * 解析条件分支配置
     */
    private Optional<Step.ConditionConfig> parseConditionConfig(Map<String, Object> stepMap) {
        Map<String, Object> cc = getMap(stepMap, "conditionConfig");
        if (cc == null || cc.isEmpty()) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        Map<String, String> branches = (Map<String, String>) cc.get("branches");

        return Optional.of(Step.ConditionConfig.builder()
                .expression(getString(cc, "expression"))
                .evaluationMethod(getString(cc, "evaluationMethod"))
                .branches(branches)
                .defaultStepId(getString(cc, "defaultStepId"))
                .build());
    }

    /**
     * 解析检查点配置（如果存在）
     */
    private Optional<Step.CheckpointConfig> parseCheckpoint(Map<String, Object> stepMap) {
        Map<String, Object> cp = getMap(stepMap, "checkpoint");
        if (cp == null || cp.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Step.CheckpointConfig.builder()
                .type(Step.CheckpointConfig.CheckpointType.valueOf(getString(cp, "type")))
                .question(getString(cp, "question"))
                .requiredScopes(getStringList(cp, "requiredScopes"))
                .timeoutMinutes(getInt(cp, "timeoutMinutes", 30))
                .onAgree(getString(cp, "onAgree"))
                .onReject(getString(cp, "onReject"))
                .build());
    }

    /**
     * 提供默认兜底得执行模式
     */
    private OrchestrationPlan.ExecutionMode detectMode(Map<String, Object> planMap) {
        try {
            return OrchestrationPlan.ExecutionMode.valueOf(
                    (String) planMap.getOrDefault("executionMode", "SEQUENTIAL"));
        } catch (Exception e) {
            return OrchestrationPlan.ExecutionMode.SEQUENTIAL;
        }
    }

    // ========== 安全的 Map 取值工具方法 ==========

    @SuppressWarnings("unchecked")
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<Map<String, Object>>) value : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<String>) value : List.of();
    }


}