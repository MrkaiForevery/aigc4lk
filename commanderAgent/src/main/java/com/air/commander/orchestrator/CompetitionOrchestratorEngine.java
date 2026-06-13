package com.air.commander.orchestrator;

import com.air.commander.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * plan生成的竞争型引擎
 * 针对同一个用户的需求，用不同的大模型，同一套提示词,生成多种plan，最后用另外一个大模型进行评估择优
 * CandidateGenerator → 多模型并行生成候选paln
 * PlanEvaluator → LLM评估择优
 */
@Slf4j
@Component
public class CompetitionOrchestratorEngine {

    private final CandidateGenerator candidateGenerator;
    private final PlanEvaluator planEvaluator;

    public CompetitionOrchestratorEngine(CandidateGenerator candidateGenerator,
                                         PlanEvaluator planEvaluator) {
        this.candidateGenerator = candidateGenerator;
        this.planEvaluator = planEvaluator;
    }

    /**
     * 三阶段生成执行计划
     */
    public OrchestrationPlan generatePlan(String userInput, MemoryContext memoryCtx) {
        long totalStart = System.currentTimeMillis();
        // ========== 多模型并行生成候选 ==========
        log.info("=== plan执行计划候选生成开始 ===");
        List<CandidateGenerator.CandidatePlan> candidates = candidateGenerator.generate(userInput, memoryCtx);
        log.info("阶段2完成: 生成{}个有效候选:{} duration={}ms",
                candidates.size(),candidates, System.currentTimeMillis() - totalStart);

        if (candidates.isEmpty()) {
            log.warn("无有效候选计划，使用降级策略");
            return buildFallbackPlan(userInput);
        }

        // ==========评估择优 ==========
        log.info("=== plan评估择优开始 ===");
        OrchestrationPlan winner = planEvaluator.evaluate(candidates, userInput, memoryCtx);
        log.info("评估择优阶段完成: 选择候选{}, totalDuration={}ms",
                winner.getPlanId(), System.currentTimeMillis() - totalStart);

        // ========== 后处理：注入userQuery ==========
        for (Step step : winner.getSteps()) {
            if (step.getInput() == null) step.setInput(new HashMap<>());
            step.getInput().put("userQuery", userInput);
        }

        return winner;
    }

    /**
     * 降级兜底计划
     */
    private OrchestrationPlan buildFallbackPlan(String userInput) {
        Step step = Step.builder()
                .id("step1")
                .type(Step.StepType.LLM_CALL)
                .task("直接回答用户问题")
                .input(Map.of("userQuery", userInput))
                .build();
        return OrchestrationPlan.builder()
                .planId("fallback-" + UUID.randomUUID().toString().substring(0, 8))
                .executionMode(OrchestrationPlan.ExecutionMode.SEQUENTIAL)
                .steps(List.of(step))
                .build();
    }
}