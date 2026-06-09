package com.air.commander.orchestrator;

import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 阶段2：候选计划生成器
 * <p>
 * 职责：并行调用多个模型，基于需求分析生成多个候选执行计划
 * 模型：qwen-long、qwen-plus、qwen-turbo（并行）
 */
@Slf4j
@Component
public class CandidateGenerator {

    private final DynamicOrchestrator dynamicOrchestrator;
    private final PlanValidator planValidator;

    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(20);

    public CandidateGenerator(DynamicOrchestrator dynamicOrchestrator,
                              PlanValidator planValidator) {
        this.dynamicOrchestrator = dynamicOrchestrator;
        this.planValidator = planValidator;
    }

    /**
     * 并行生成多个候选计划
     */
    public List<CandidatePlan> generate(String userInput, MemoryContext memoryCtx) {

        // 定义候选任务
        List<CandidateTask> tasks = List.of(
                new CandidateTask("A", "fastModelClient"),
                new CandidateTask("B", "reasoningModelClient"),
                new CandidateTask("C", "plusModelClient")
        );

        // 并行提交
        List<CompletableFuture<CandidatePlan>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> {
                            long start = System.currentTimeMillis();
                            OrchestrationPlan orchestrationPlan = dynamicOrchestrator.generatePlan(userInput, memoryCtx, task.choseChatClientName);
                            long end = System.currentTimeMillis();
                            return new CandidatePlan(task.id(), task.choseChatClientName, orchestrationPlan, end - start);
                        },
                        parallelExecutor)
                )
                .toList();

        // 等待所有完成（最多60秒）
        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(60, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("等待候选计划超时", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    // ==================== 内部类 ====================

    private record CandidateTask(String id, String choseChatClientName) {
    }

    /**
     * 候选计划包装类
     */
    public record CandidatePlan(String id, String choseChatClientName, OrchestrationPlan plan, long durationMs) {
    }
}