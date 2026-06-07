package com.air.commander.orchestrator;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.resilience.ResilienceManager;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动态编排器
 * 用于执行步骤计划
 */
@Component
public class DynamicOrchestrator {

    private final ChatClient chatClient;
    private final BaseNacosA2ARouter baseNacosA2ARouter;
    private final PlanValidator planValidator;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;
    private final PromptManagerBuilder promptManagerBuilder;

    public DynamicOrchestrator(@Qualifier("reasoningModelClient") ChatClient reasoningModelClient,
                               BaseNacosA2ARouter baseNacosA2ARouter,
                               PlanValidator planValidator,
                               ResilienceManager resilienceManager,
                               PromptManagerBuilder promptManagerBuilder) {
        this.chatClient = reasoningModelClient;
        this.baseNacosA2ARouter = baseNacosA2ARouter;
        this.planValidator = planValidator;
        this.resilienceManager = resilienceManager;
        this.objectMapper = new ObjectMapper();
        this.promptManagerBuilder = promptManagerBuilder;
    }


    public OrchestrationPlan generatePlan(String userInput, MemoryContext memoryCtx) {
        Set<AgentCardWrapper> availableAgents = baseNacosA2ARouter.getAvailableAgents();
        String prompt = promptManagerBuilder.buildDynamicOrchestratorGeneratePlanPrompt(userInput, memoryCtx, availableAgents);
        String llmOutput = resilienceManager.executeWithFullProtection(
                "llm-reasoning-model",
                () -> chatClient.prompt(prompt).call().content(),
                () -> "{\"steps\": [{\"id\":\"step1\",\"type\":\"LLM_CALL\",\"task\":\"ANSWER\"}]}"
        );
        try {
            Map<String, Object> planMap = objectMapper.readValue(llmOutput, Map.class);
            List<Step> steps = parseSteps(planMap);
            OrchestrationPlan plan = OrchestrationPlan.builder()
                    .planId(UUID.randomUUID().toString())
                    .executionMode(detectMode(planMap))
                    .steps(steps)
                    .build();
            if (!planValidator.validate(plan).isValid()) {
                // 重试一次
                llmOutput = chatClient.prompt(prompt + "\n请修正错误").call().content();
                planMap = objectMapper.readValue(llmOutput, Map.class);
                steps = parseSteps(planMap);
                plan.setSteps(steps);
            }

            // 注意:强制将 userInput 注入到每个步骤的 input 中
            for (Step step : plan.getSteps()) {
                if (step.getInput() == null) {
                    step.setInput(new HashMap<>());
                }
                step.getInput().put("userQuery", userInput);
            }

            return plan;
        } catch (Exception e) {
            throw new RuntimeException("Plan generation failed", e);
        }
    }

    private List<Step> parseSteps(Map<String, Object> planMap) {
        List<Map<String, Object>> stepList = (List<Map<String, Object>>) planMap.get("steps");
        List<Step> steps = new ArrayList<>();
        for (Map<String, Object> s : stepList) {
            steps.add(Step.builder()
                    .id((String) s.get("id"))
                    .type(Step.StepType.valueOf((String) s.get("type")))
                    .agent((String) s.get("agent"))
                    .task((String) s.get("task"))
                    .input((Map<String, Object>) s.get("input"))
                    .dependsOn((List<String>) s.get("dependsOn"))
                    .build());
        }
        return steps;
    }

    private OrchestrationPlan.ExecutionMode detectMode(Map<String, Object> planMap) {
        try {
            return OrchestrationPlan.ExecutionMode.valueOf(
                    (String) planMap.getOrDefault("executionMode", "SEQUENTIAL"));
        } catch (Exception e) {
            return OrchestrationPlan.ExecutionMode.SEQUENTIAL;
        }
    }
}