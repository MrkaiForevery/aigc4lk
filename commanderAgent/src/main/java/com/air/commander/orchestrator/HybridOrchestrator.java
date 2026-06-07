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
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 混合编排器
 * 核心编排逻辑句柄类
 */
@Service
@RequiredArgsConstructor
public class HybridOrchestrator {

    private final MemoryContextBuilder memoryContextBuilder;
    private final IntentClassifier intentClassifier;
    private final TemplateExecutor templateExecutor;
    private final DynamicOrchestrator dynamicOrchestrator;
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
        } else {
            plan = dynamicOrchestrator.generatePlan(userInput, memoryCtx);
        }

        // 4. 执行计划
        String xid = RootContext.getXID();
        List<ExecutionResult> results = graphExecutor.execute(plan, threadId, userId, tokens, xid, memoryCtx);

        // 5. 异步质量评估与记忆更新
        CompletableFuture.runAsync(() -> {
            int score = qualityAssessor.evaluate(plan, results);
            memoryUpdatePipeline.update(threadId, userId, plan, results, score, memoryCtx);
        });

        // 6. 构建返回结果
        return ExecutionPlan.builder()
                .mode(intent.isTemplate() ? ExecutionPlan.ModeType.TEMPLATE : ExecutionPlan.ModeType.DYNAMIC)
                .planId(plan.getPlanId())
                .results(results)
                .interrupted(results.stream().anyMatch(r -> r.getCommand() != null))
                .summary("Executed")
                .xid(xid)
                .build();
    }

    /**
     * 从中断点恢复执行（检查点恢复）
     */
    @GlobalTransactional(timeoutMills = 1800000)
    public ExecutionPlan resumeExecution(String xid, List<String> approvedScopes) {
        // 1. 读取检查点（注意：需要先读取，因为 resume 会删除）
        InterruptContext ctx = (InterruptContext) redissonClient.getBucket("interrupt:" + xid).get();
        if (ctx == null) {
            throw new RuntimeException("检查点不存在或已过期: " + xid);
        }

        // 2. 获取用户凭证
        Map<String, String> tokens = credentialService.approve(ctx.getUserId(), approvedScopes);

        // 3. 恢复全局事务（在 resume 中完成）
        interruptHandler.resume(xid, approvedScopes); // 内部会恢复事务并删除检查点

        // 4. 反序列化编排计划
        OrchestrationPlan plan;
        try {
            plan = objectMapper.readValue(ctx.getPlanJson(), OrchestrationPlan.class);
        } catch (Exception e) {
            throw new RuntimeException("无法反序列化编排计划", e);
        }

        // 5. 构建剩余步骤（跳过已完成的步骤）
        List<Step> allSteps = graphBuilder.buildExecutionOrder(plan);
        List<Step> remainingSteps = allSteps.subList(ctx.getStepIndex() + 1, allSteps.size());

        // 6. 创建仅包含剩余步骤的新计划
        OrchestrationPlan remainingPlan = OrchestrationPlan.builder()
                .planId(plan.getPlanId())
                .executionMode(plan.getExecutionMode())
                .steps(remainingSteps)
                .build();

        // 7. 重建记忆上下文（可根据需要从 ctx 恢复会话信息）
        MemoryContext memoryCtx = memoryContextBuilder.build(ctx.getUserId(), ctx.getThreadId(), "");

        // 8. 执行剩余步骤
        List<ExecutionResult> newResults = graphExecutor.execute(
                remainingPlan, ctx.getThreadId(), ctx.getUserId(),
                tokens, xid, memoryCtx
        );

        // 9. 合并前序结果与新结果
        return buildMergedPlan(plan, ctx, newResults, xid);
    }

    /**
     * 合并前序步骤结果和新执行结果
     */
    private ExecutionPlan buildMergedPlan(OrchestrationPlan plan,
                                          InterruptContext ctx,
                                          List<ExecutionResult> newResults,
                                          String xid) {
        List<ExecutionResult> allResults = new ArrayList<>();

        // 1. 从检查点恢复前序步骤的结果（已完成的步骤）
        Map<String, Object> runtimeContext = ctx.getRuntimeContext();
        for (int i = 0; i <= ctx.getStepIndex(); i++) {
            Step step = plan.getSteps().get(i);
            if (step.getId().equals(ctx.getCurrentStepId())) {
                // 中断步骤本身（返回了 command）
                allResults.add(ExecutionResult.builder()
                        .stepId(step.getId())
                        .success(false)
                        .command(ExecutionResult.Command.builder()
                                .type(ctx.getCommandType())
                                .message(ctx.getQuestion())
                                .requiredScopes(ctx.getRequiredScopes())
                                .build())
                        .build());
            } else {
                // 已完成的步骤，输出从 context 中取出
                Object output = runtimeContext.get(step.getId() + ".output");
                allResults.add(ExecutionResult.builder()
                        .stepId(step.getId())
                        .success(true)
                        .output(output instanceof Map ? (Map<String, Object>) output : null)
                        .build());
            }
        }

        // 2. 追加新执行的步骤结果
        allResults.addAll(newResults);

        return ExecutionPlan.builder()
                .mode(ExecutionPlan.ModeType.valueOf(ctx.getMode()))
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

}