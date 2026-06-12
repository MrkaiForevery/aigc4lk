package com.air.commander.orchestrator;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.chat.ChatClientSelector;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.PlanEvaluationResult;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划评估与择优
 * 
 * 职责：对多个候选计划进行多维度评估，选出最优计划
 * 模型：qwen-max（评判者）
 */
@Slf4j
@Component
public class PlanEvaluator {


    private final ChatClient reasoningModelClient;

    private final PlanValidator planValidator;
    private final PromptManagerBuilder promptManagerBuilder;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;

    public PlanEvaluator(ChatClientSelector chatClientSelector,
                         PlanValidator planValidator,
                         PromptManagerBuilder promptManagerBuilder,
                         ResilienceManager resilienceManager,
                         ObjectMapper objectMapper) {
        this.reasoningModelClient = chatClientSelector.getClient("reasoningModelClient");
        this.planValidator = planValidator;
        this.resilienceManager = resilienceManager;
        this.objectMapper = objectMapper;
        this.promptManagerBuilder = promptManagerBuilder;
    }

    /**
     * 评估并选出最优计划
     */
    public OrchestrationPlan evaluate(List<CandidateGenerator.CandidatePlan> candidates, String userInput, MemoryContext memoryCtx) {

        // 先进行规则校验，过滤掉不合格的候选
        List<CandidateGenerator.CandidatePlan> validCandidates = candidates.stream()
                .filter(c -> planValidator.validateOrchestrationPlan(c.plan()).isValid())
                .toList();

        if (validCandidates.isEmpty()) {
            log.warn("所有候选计划校验失败，使用第一个原始候选");
            validCandidates = candidates;
        }

        if (validCandidates.size() == 1) {
            log.info("仅有一个有效候选，直接采用: {}", validCandidates.get(0).id());
            return validCandidates.get(0).plan();
        }

        // LLM评估择优
        String prompt =promptManagerBuilder.buildEvaluationPrompt(validCandidates,userInput,memoryCtx);
        String llmOutput = resilienceManager.executeWithFullProtection(
                "llm-reasoning-model",
                () -> reasoningModelClient.prompt(prompt).call().content(),
                () -> "{\"winner\":\"A\",\"scores\":{},\"reason\":\"降级选择\"}"
        );

        PlanEvaluationResult result = parseEvaluationResult(llmOutput);

        return validCandidates.stream()
                .filter(c -> c.id().equals(result.getWinner()))
                .findFirst()
                .map(CandidateGenerator.CandidatePlan::plan)
                .orElse(validCandidates.get(0).plan());
    }

    /**
     * 解析评估结果
     */
    private PlanEvaluationResult parseEvaluationResult(String llmOutput) {
        try {
            Map<String, Object> map = objectMapper.readValue(llmOutput, Map.class);
            return objectMapper.convertValue(map, PlanEvaluationResult.class);
        } catch (Exception e) {
            log.error("解析评估结果失败，降级处理", e);
            return PlanEvaluationResult.builder()
                    .winner("A")
                    .scores(Map.of())
                    .reason("降级解析失败")
                    .build();
        }
    }

}