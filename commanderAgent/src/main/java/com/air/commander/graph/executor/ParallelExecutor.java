package com.air.commander.graph.executor;

import com.air.commander.graph.GraphBuilder;
import com.air.commander.graph.common.GraphCommonDataProcessor;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ParallelExecutor {

    private final GraphBuilder graphBuilder;
    private final StepUnitExecutor stepUnitExecutor;
    private final GraphCommonDataProcessor graphCommonDataProcessor;

    // 固定线程池，替代虚拟线程
    private final ExecutorService parallelExecutor;

    public ParallelExecutor(GraphBuilder graphBuilder,
                            StepUnitExecutor stepUnitExecutor,
                            GraphCommonDataProcessor graphCommonDataProcessor) {
        this.graphBuilder = graphBuilder;
        this.stepUnitExecutor = stepUnitExecutor;
        this.graphCommonDataProcessor = graphCommonDataProcessor;
        this.parallelExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public List<ExecutionResult> executeParallel(OrchestrationPlan plan,
                                                  String threadId, String userId,
                                                  Map<String, String> tokens, String xid,
                                                  MemoryContext memoryCtx,
                                                  Map<String, Object> runtimeContext) {

        log.debug("以Parallel模式开始执行任务..... ");

        long start= System.currentTimeMillis();

        // 1. 拓扑排序，识别并行组
        List<List<Step>> parallelGroups = graphBuilder.buildParallelExecutionGroups(plan.getSteps());
        List<ExecutionResult> allResults = new ArrayList<>();

        for (List<Step> group : parallelGroups) {
            // 过滤出该组中尚未执行的步骤（恢复时跳过已完成步骤）
            List<Step> activeSteps = group.stream()
                    .filter(step -> !runtimeContext.containsKey(step.getId() + ".output")
                            && !runtimeContext.containsKey(step.getId() + ".interrupted"))
                    .collect(Collectors.toList());

            // 该组全部已完成，直接跳过
            if (activeSteps.isEmpty()) {
                continue;
            }

            // 2. 处理组内单步骤
            if (group.size() == 1) {
                Step step = group.get(0);
                ExecutionResult r = stepUnitExecutor.executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx);
                allResults.add(r);

                //中断检查+失败回滚通用
                if (!graphCommonDataProcessor.postProcessStepResult(r, step, runtimeContext, plan,
                        allResults.size() - 1, xid, userId, threadId)) {
                    return allResults;
                }

            } else {
                // 3. 并行执行组内步骤
                List<CompletableFuture<ExecutionResult>> futures = group.stream()
                        .map(step -> CompletableFuture.supplyAsync(() ->
                                        stepUnitExecutor.executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx),
                                parallelExecutor))
                        .collect(Collectors.toList());

                List<ExecutionResult> groupResults = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());

                allResults.addAll(groupResults);

                // 统一后处理：先更新上下文，再检查中断和失败
                for (ExecutionResult r : groupResults) {
                    Step step = graphCommonDataProcessor.findStepById(plan.getSteps(), r.getStepId());
                    if (!graphCommonDataProcessor.postProcessStepResult(r, step, runtimeContext, plan,
                            allResults.size() - 1, xid, userId, threadId)) {
                        return allResults;
                    }
                }
            }
        }

        long end = System.currentTimeMillis();
        log.debug("以Parallel模式执行任务结束，本次耗时:{}ms ",end-start);
        return allResults;
    }
}
