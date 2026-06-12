package com.air.commander.orchestrator;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.chat.ChatClientSelector;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.model.ValidationResult;
import com.air.commander.resilience.ResilienceManager;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动态编排器
 * 用于执行步骤计划
 */
@Slf4j
@Component
public class DynamicOrchestrator {

    private final Map<String,ChatClient> chatClientMap;
    private final BaseNacosA2ARouter baseNacosA2ARouter;
    private final PlanValidator planValidator;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;
    private final PromptManagerBuilder promptManagerBuilder;

    public DynamicOrchestrator(ChatClientSelector chatClientSelector,
                               BaseNacosA2ARouter baseNacosA2ARouter,
                               PlanValidator planValidator,
                               ResilienceManager resilienceManager,
                               PromptManagerBuilder promptManagerBuilder) {
        this.chatClientMap = chatClientSelector.getAllMap();
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
        ChatClient chatClient = this.chatClientMap.get(choseChatClientBeanName);
        String llmOutput = resilienceManager.executeWithFullProtection(
                "llm-reasoning-model",
                () -> chatClient.prompt(prompt).call().content(),
                () -> "{\"steps\": [{\"id\":\"step1\",\"type\":\"LLM_CALL\",\"task\":\"ANSWER\"}]}"
        );
        try {
            Map<String, Object> planMap = objectMapper.readValue(llmOutput, Map.class);
            planMap.put("planId",UUID.randomUUID().toString());
            OrchestrationPlan plan = objectMapper.convertValue(planMap, OrchestrationPlan.class);
            return plan;
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
}