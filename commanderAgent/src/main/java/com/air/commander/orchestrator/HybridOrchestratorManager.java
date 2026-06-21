package com.air.commander.orchestrator;

import com.air.commander.graph.GraphExecutorEngine;
import com.air.commander.intent.IntentClassifier;
import com.air.commander.memory.MemoryContextBuilder;
import com.air.commander.memory.MemoryUpdatePipeline;
import com.air.commander.model.*;
import com.air.commander.quality.QualityAssessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 混合模式编排器
 * 核心编排逻辑句柄类
 * 模式1: 静态模板匹配方式(template)生成严格的预定义好的执行plan
 * 模式2: 由LLM机制自由动态的生成复杂的执行plan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridOrchestratorManager {

    private final MemoryContextBuilder memoryContextBuilder;
    private final IntentClassifier intentClassifier;
    private final TemplatePlanGenerator templatePlanGenerator;
    private final CompetitionOrchestratorEngine competitionOrchestratorEngine;
    private final GraphExecutorEngine graphExecutorEngine;
    private final MemoryUpdatePipeline memoryUpdatePipeline;
    private final QualityAssessor qualityAssessor;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @GlobalTransactional(timeoutMills = 1800000)
    public ExecutionPlan execute(ExecuteRequest request) {
        //请求id-幂等需要
        String requestId = UUID.randomUUID().toString();
        //用户id-用户识别
        String userId = request.userId();
        //threadId-会话隔离
        String threadId = request.threadId();
        //用户输入问题
        String userInput = request.input();
        //用户鉴权扩展参数
        Map<String, String> tokens = request.tokens();

        // 1. 构建记忆上下文
        MemoryContext memoryCtx = memoryContextBuilder.build(userId, threadId, userInput);

        // 2. 意图识别
        IntentResult intent = intentClassifier.classify(userInput, memoryCtx);

        // 3. 生成编排计划
        OrchestrationPlan plan;
        if (intent.isTemplate()) {
            plan = templatePlanGenerator.loadAndPersonalize(intent.templateId(), userInput, memoryCtx);
            plan.setMode(ExecutionPlan.ModeType.TEMPLATE);
        } else {
            // 使用竞争式多LLM模型回答选择结果
            plan = competitionOrchestratorEngine.generatePlan(userInput, memoryCtx);
            plan.setMode(ExecutionPlan.ModeType.DYNAMIC);
        }

        //设置幂等性requestId
        plan.setRelationRequestId(requestId);

        // 4. 执行编排好的计划
        String xid = RootContext.getXID();
        List<ExecutionResult> results = graphExecutorEngine.execute(plan, threadId, userId, tokens, xid, memoryCtx);

        // 5. 构建返回结果
        ExecutionPlan originalExecutedResult = ExecutionPlan.builder()
                .mode(plan.getMode())
                .planId(plan.getPlanId())
                .results(results)
                .interrupted(results.stream().anyMatch(r -> r.getCommand() != null))
                .summary("Executed")
                .xid(xid)
                .build();

        // 6. 记忆更新--历史记忆存储
        CompletableFuture.runAsync(() -> {
            memoryUpdatePipeline.update(threadId, userId, userInput, plan, results, memoryCtx);
        });


        return trimResults(originalExecutedResult);
    }

    /**
     * 从中断点恢复执行（检查点恢复）
     */
    public ExecutionPlan resumeExecution(String xid, Map<String, String> tokens) {
        InterruptContext ctx = loadCheckpoint(xid);
        RootContext.bind(xid);

        try {
            OrchestrationPlan plan = deserializePlan(ctx.getPlanJson());
            Map<String, Object> runtimeContext = ctx.getRuntimeContext();

            // 直接使用完整步骤列表，不再提前过滤，让后续具体执行时，其内部方法进行排序
            List<Step> allSteps = plan.getSteps();

            if (allSteps.isEmpty()) {
                commitTransaction(xid);
                return buildFinalResult(plan, ctx, plan.getSteps(), Collections.emptyList(), xid);
            }

            // 保留原执行模式，不强制改为顺序
            OrchestrationPlan remainingPlan = OrchestrationPlan.builder()
                    .mode(plan.getMode())
                    .planId(plan.getPlanId())
                    .executionMode(plan.getExecutionMode())  // 保留原模式
                    .steps(allSteps)
                    .build();

            MemoryContext memoryCtx = memoryContextBuilder.build(ctx.getUserId(), ctx.getThreadId(), "");
            List<ExecutionResult> newResults = graphExecutorEngine.execute(
                    remainingPlan,
                    ctx.getThreadId(),
                    ctx.getUserId(),
                    tokens,
                    xid,
                    memoryCtx,
                    runtimeContext
            );

            return buildFinalResult(plan, ctx, plan.getSteps(), newResults, xid);
        } finally {
            RootContext.unbind();
            redissonClient.getBucket("interrupt:" + xid).delete();
        }
    }

    /**
     * 基于 runtimeContext 找出未完成的步骤
     * 不依赖 stepIndex，兼容顺序和并行模式
     */
    private List<Step> getRemainingSteps(List<Step> allSteps, Map<String, Object> runtimeContext) {
        return allSteps.stream()
                .filter(step -> !runtimeContext.containsKey(step.getId() + ".output")) //过滤已经正常完成的步骤
                .filter(step -> !runtimeContext.containsKey(step.getId() + ".interrupted")) // 过滤中断步骤
                .collect(Collectors.toList());
    }


    // 委托和封装方法
    private InterruptContext loadCheckpoint(String xid) {
        InterruptContext ctx = (InterruptContext) redissonClient.getBucket("interrupt:" + xid).get();
        if (ctx == null) {
            throw new RuntimeException("检查点不存在或已过期: " + xid);
        }
        return ctx;
    }

    private OrchestrationPlan deserializePlan(String planJson) {
        try {
            return objectMapper.readValue(planJson, OrchestrationPlan.class);
        } catch (Exception e) {
            throw new RuntimeException("无法反序列化编排计划", e);
        }
    }

    private ExecutionPlan buildFinalResult(OrchestrationPlan plan, InterruptContext ctx, List<Step> allSteps, List<ExecutionResult> newResults, String xid) {
        boolean anyFailed = newResults.stream().anyMatch(r -> !r.isSuccess());
        if (anyFailed) {
            rollbackTransaction(xid);
        } else {
            commitTransaction(xid);
        }
        return trimResults(buildMergedPlan(plan, ctx, allSteps, newResults, xid));
    }

    // Seata 事务操作封装
    private void commitTransaction(String xid) {
        try {
            GlobalTransactionContext.reload(xid).commit();
        } catch (TransactionException e) {
            throw new RuntimeException(e);
        }
    }

    private void rollbackTransaction(String xid) {
        try {
            GlobalTransactionContext.reload(xid).rollback();
        } catch (TransactionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 合并前序步骤结果和新执行结果
     */
    private ExecutionPlan buildMergedPlan(OrchestrationPlan plan,
                                          InterruptContext ctx,
                                          List<Step> allSteps,
                                          List<ExecutionResult> newResults,
                                          String xid) {
        Map<String, Object> runtimeContext = ctx.getRuntimeContext();
        List<ExecutionResult> allResults = new ArrayList<>();

        // 【改动】遍历所有步骤，通过 runtimeContext 判断是否已完成
        for (Step step : allSteps) {
            String outputKey = step.getId() + ".output";
            String interruptKey = step.getId() + ".interrupted";
            if (runtimeContext.containsKey(outputKey)) {
                // 已完成步骤，从上下文取输出
                allResults.add(ExecutionResult.builder()
                        .stepId(step.getId())
                        .success(true)
                        .executionStatus(ExecutionResult.ExecutionStatus.DONE)
                        .output(runtimeContext.get(outputKey) instanceof Map ?
                                (Map<String, Object>) runtimeContext.get(outputKey) : null)
                        .build());
            } else if (runtimeContext.containsKey(interruptKey)) {
                // 中断步骤本身
                if (step.getId().equals(ctx.getCurrentStepId())) {
                    allResults.add(ExecutionResult.builder()
                            .stepId(step.getId())
                            .success(true)
                            .executionStatus(ExecutionResult.ExecutionStatus.DONE)
                            .command(ExecutionResult.Command.builder()
                                    .type(ctx.getCommandType())
                                    .message(ctx.getQuestion())
                                    .requiredScopes(ctx.getRequiredScopes())
                                    .build())
                            .build());
                }
            }
            // 其他未完成的步骤的结果由 newResults 补充
        }

        allResults.addAll(newResults);

        return ExecutionPlan.builder()
                .mode(ctx.getMode())
                .planId(plan.getPlanId())
                .results(allResults)
                .interrupted(false)
                .summary("恢复执行完成")
                .xid(xid)
                .build();
    }


    // 请求 DTO
    public record ExecuteRequest(String userId, String threadId, String input, Map<String, String> tokens) {
    }


    /**
     * 精简 ExecutionPlan 中的 results，避免返回过大的响应体导致前端解析失败。
     * - 对于中断步骤（包含 command），保留完整 output（含 previousStepOutput 和 question）。
     * - 对于其他已完成步骤，仅保留 stepId 和 success 状态，移除庞大的 output 内容。
     */
    private ExecutionPlan trimResults(ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.getResults() == null) {
            return executionPlan;
        }

        List<ExecutionResult> trimmedResults = executionPlan.getResults().stream()
                .map(result -> {
                    // 如果是中断步骤（有 command），保留完整内容
                    if (result.getCommand() != null) {
                        return result;
                    }
                    // 否则只保留关键信息，删除大段输出
                    return ExecutionResult.builder()
                            .stepId(result.getStepId())
                            .success(result.isSuccess())
                            .executionStatus(result.getExecutionStatus())
                            .output(Map.of("summary", "Step completed",
                                    "content",result.getOutput().get("content")))
                            .durationMs(result.getDurationMs())
                            .error(result.getError())  // 如果有错误，保留错误信息
                            .build();
                })
                .collect(Collectors.toList());

        return ExecutionPlan.builder()
                .mode(executionPlan.getMode())
                .planId(executionPlan.getPlanId())
                .results(trimmedResults)
                .interrupted(executionPlan.isInterrupted())
                .summary(executionPlan.getSummary())
                .xid(executionPlan.getXid())
                .context(executionPlan.getContext())
                .build();
    }
}