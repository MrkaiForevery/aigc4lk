package com.air.commander.orchestrator;

import com.air.commander.credential.CredentialService;
import com.air.commander.intent.IntentClassifier;
import com.air.commander.interrupt.InterruptHandler;
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
import java.util.stream.IntStream;

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
    private final TemplateExecutor templateExecutor;
    private final CompetitionOrchestratorEngine competitionOrchestratorEngine;
    private final GraphExecutor graphExecutor;
    private final MemoryUpdatePipeline memoryUpdatePipeline;
    private final QualityAssessor qualityAssessor;
    private final InterruptHandler interruptHandler;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final CredentialService credentialService;
    private final GraphBuilder graphBuilder;

    @GlobalTransactional(timeoutMills = 1800000)
    public ExecutionPlan execute(ExecuteRequest request) {
        String userId = request.userId();
        String threadId = request.threadId();
        String userInput = request.input();
        Map<String, String> tokens = request.tokens();

        // 1. 构建记忆上下文
        MemoryContext memoryCtx = memoryContextBuilder.build(userId, threadId, userInput);

        // 2. 意图识别
        IntentResult intent = intentClassifier.classify(userInput, memoryCtx);

        // 3. 生成编排计划
        OrchestrationPlan plan;
        if (intent.isTemplate()) {
            plan = templateExecutor.loadAndPersonalize(intent.templateId(), userInput, memoryCtx);
            plan.setMode(ExecutionPlan.ModeType.TEMPLATE);
        } else {
            // 使用竞争式多LLM模型回答选择结果
            plan = competitionOrchestratorEngine.generatePlan(userInput, memoryCtx);
            plan.setMode(ExecutionPlan.ModeType.DYNAMIC);
        }

        // 4. 执行计划
        String xid = RootContext.getXID();
        List<ExecutionResult> results = graphExecutor.execute(plan, threadId, userId, tokens, xid, memoryCtx);

        // 5. 异步质量评估与记忆更新
        final OrchestrationPlan finalPlan = plan;
        CompletableFuture.runAsync(() -> {
            int score = qualityAssessor.evaluate(finalPlan, results);
            memoryUpdatePipeline.update(threadId, userId, finalPlan, results, score, memoryCtx);
        });

        // 6. 构建返回结果
        ExecutionPlan originalExecutedResult = ExecutionPlan.builder()
                .mode(plan.getMode())
                .planId(plan.getPlanId())
                .results(results)
                .interrupted(results.stream().anyMatch(r -> r.getCommand() != null))
                .summary("Executed")
                .xid(xid)
                .build();

        return trimResults(originalExecutedResult);
    }

    /**
     * 从中断点恢复执行（检查点恢复）
     */
    public ExecutionPlan resumeExecution(String xid, Map<String, String> tokens) {
        //从redis上获取checkPoint信息
        InterruptContext ctx = loadCheckpoint(xid);
        //兜底:重复绑定事务id
        RootContext.bind(xid);

        try {
            //序列化plan
            OrchestrationPlan plan = deserializePlan(ctx.getPlanJson());
            //还原所有的step步骤
            List<Step> allSteps = graphBuilder.buildExecutionOrder(plan);

            //如果现在的checkPoint是最后一个step，直接返回
            if (hasNoRemainingSteps(ctx, allSteps)) {
                commitTransaction(xid);
                return buildFinalResult(plan, ctx, allSteps, Collections.emptyList(), xid);
            }

            //继续执行后续步骤
            List<ExecutionResult> newResults = executeRemainingSteps(plan, ctx, allSteps, tokens, xid);

            //对结果进行判断，是否回滚或者提交事务
            return buildFinalResult(plan, ctx, allSteps, newResults, xid);
        } finally {
            //释放此次currentCheckPoint分布式全局事务锁
            RootContext.unbind();
            //释放redis里面currentCheckPoint信息
            redissonClient.getBucket("interrupt:" + xid).delete();
        }
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

    private boolean hasNoRemainingSteps(InterruptContext ctx, List<Step> allSteps) {
        return ctx.getStepIndex() + 1 >= allSteps.size();
    }

    private List<ExecutionResult> executeRemainingSteps(OrchestrationPlan plan, InterruptContext ctx, List<Step> allSteps, Map<String, String> tokens, String xid) {
        List<Step> remainingSteps = allSteps.subList(ctx.getStepIndex() + 1, allSteps.size());

        OrchestrationPlan remainingPlan = OrchestrationPlan.builder()
                .planId(plan.getPlanId())
                .executionMode(plan.getExecutionMode())
                .steps(remainingSteps)
                .build();

        MemoryContext memoryCtx = memoryContextBuilder.build(ctx.getUserId(), ctx.getThreadId(), "");

        return graphExecutor.execute(
                remainingPlan,
                ctx.getThreadId(),
                ctx.getUserId(),
                tokens,
                xid,
                memoryCtx,
                ctx.getRuntimeContext()
        );
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
                                          List<Step> orderedSteps,
                                          List<ExecutionResult> newResults,
                                          String xid) {
        Map<String, Object> runtimeContext = ctx.getRuntimeContext();

        // 1. 构建已完成步骤的结果（包括中断步骤）
        List<ExecutionResult> previousResults = IntStream.rangeClosed(0, ctx.getStepIndex())
                .mapToObj(i -> {
                    Step step = orderedSteps.get(i);
                    if (step.getId().equals(ctx.getCurrentStepId())) {
                        // 中断步骤本身
                        return ExecutionResult.builder()
                                .stepId(step.getId())
                                .success(false)
                                .command(ExecutionResult.Command.builder()
                                        .type(ctx.getCommandType())
                                        .message(ctx.getQuestion())
                                        .requiredScopes(ctx.getRequiredScopes())
                                        .build())
                                .build();
                    } else {
                        // 已完成步骤，从上下文取输出
                        Object output = runtimeContext.get(step.getId() + ".output");
                        return ExecutionResult.builder()
                                .stepId(step.getId())
                                .success(true)
                                .output(output instanceof Map ? (Map<String, Object>) output : null)
                                .build();
                    }
                })
                .collect(Collectors.toList());

        // 2. 合并所有结果
        List<ExecutionResult> allResults = new ArrayList<>(previousResults);
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
                            .output(Map.of("summary", "Step completed")) // 或直接 .output(null)
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