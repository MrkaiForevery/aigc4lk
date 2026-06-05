package com.air.commander.orchestrator;

import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.resilience.ResilienceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 流程图的具体实施执行器
 */
@Component
@RequiredArgsConstructor
public class GraphExecutor {

    private final BaseNacosA2ARouter a2aRouter;
    private final InterruptHandler interruptHandler;
    private final GraphBuilder graphBuilder;
    private final ResilienceManager resilience;

    // 固定线程池，替代虚拟线程
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    public List<ExecutionResult> execute(OrchestrationPlan plan,
                                         String threadId,
                                         String userId,
                                         Map<String, String> tokens,
                                         String xid,
                                         MemoryContext memoryCtx) {
        return switch (plan.getExecutionMode()) {
            case SEQUENTIAL -> executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
            case PARALLEL -> executeParallel(plan, threadId, userId, tokens, xid, memoryCtx);
            case CONDITIONAL -> executeConditional(plan, threadId, userId, tokens, xid, memoryCtx);
            case ITERATIVE_CORRECTION -> executeIterative(plan, threadId, userId, tokens, xid, memoryCtx);
            case COMPETITIVE -> executeCompetitive(plan, threadId, userId, tokens, xid, memoryCtx);
            case PIPELINE -> executePipeline(plan, threadId, userId, tokens, xid, memoryCtx);
        };
    }

    private List<ExecutionResult> executeSequential(OrchestrationPlan plan,
                                                    String threadId,
                                                    String userId,
                                                    Map<String, String> tokens,
                                                    String xid,
                                                    MemoryContext memoryCtx) {
        List<Step> ordered = graphBuilder.buildExecutionOrder(plan);
        Map<String, Object> context = new ConcurrentHashMap<>();
        List<ExecutionResult> results = new ArrayList<>();
        for (Step step : ordered) {
            ExecutionResult r = executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx);
            results.add(r);
            if (r.getCommand() != null) break;
            if (r.isSuccess()) context.put(step.getId() + ".output", r.getOutput());
            if (!r.isSuccess() && step.isMandatory()) break;
        }
        return results;
    }

    private List<ExecutionResult> executeParallel(OrchestrationPlan plan,
                                                  String threadId,
                                                  String userId,
                                                  Map<String, String> tokens,
                                                  String xid,
                                                  MemoryContext memoryCtx) {
        List<Step> steps = plan.getSteps();
        Map<String, Object> context = new ConcurrentHashMap<>();
        // 无依赖的步骤并行执行
        List<Step> parallelSteps = steps.stream()
                .filter(s -> s.getDependsOn() == null || s.getDependsOn().isEmpty())
                .collect(Collectors.toList());

        List<CompletableFuture<ExecutionResult>> futures = parallelSteps.stream()
                .map(step -> CompletableFuture.supplyAsync(() ->
                        resilience.executeWithFullProtection(
                                "agent-" + step.getAgent(),
                                () -> executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx),
                                () -> ExecutionResult.builder()
                                        .stepId(step.getId())
                                        .success(false)
                                        .error("fallback")
                                        .build()
                        ), parallelExecutor))
                .collect(Collectors.toList());

        List<ExecutionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        results.forEach(r -> {
            if (r.isSuccess()) context.put(r.getStepId() + ".output", r.getOutput());
        });

        // 注：这里仅实现了一轮并行，实际生产需循环处理依赖满足的后续步骤，此处从简
        return results;
    }

    private List<ExecutionResult> executeConditional(OrchestrationPlan plan,
                                                     String threadId,
                                                     String userId,
                                                     Map<String, String> tokens,
                                                     String xid,
                                                     MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展条件分支逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private List<ExecutionResult> executeIterative(OrchestrationPlan plan,
                                                   String threadId,
                                                   String userId,
                                                   Map<String, String> tokens,
                                                   String xid,
                                                   MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展循环纠正逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private List<ExecutionResult> executeCompetitive(OrchestrationPlan plan,
                                                     String threadId,
                                                     String userId,
                                                     Map<String, String> tokens,
                                                     String xid,
                                                     MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展竞争选择逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private List<ExecutionResult> executePipeline(OrchestrationPlan plan,
                                                  String threadId,
                                                  String userId,
                                                  Map<String, String> tokens,
                                                  String xid,
                                                  MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展流水线传递逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private ExecutionResult executeSingleStep(Step step,
                                              Map<String, Object> context,
                                              String threadId,
                                              String userId,
                                              Map<String, String> tokens,
                                              String xid,
                                              MemoryContext memoryCtx) {
        return switch (step.getType()) {
            case A2A_DELEGATE -> a2aRouter.callAgent(step, context, tokens, threadId, xid, memoryCtx);
            case LLM_CALL -> ExecutionResult.builder()
                    .stepId(step.getId())
                    .success(true)
                    .output(Map.of("content", "LLM response"))
                    .build();
            case INTERRUPT -> ExecutionResult.builder()
                    .stepId(step.getId())
                    .success(false)
                    .command(ExecutionResult.Command.builder()
                            .type("REQUEST_CONFIRM")
                            .message(step.getQuestion())
                            .requiredScopes(List.of())
                            .build())
                    .build();
        };
    }
}