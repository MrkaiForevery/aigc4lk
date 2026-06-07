package com.air.commander.orchestrator;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 流程图的具体实施执行器
 */
@Slf4j
@Component
public class GraphExecutor {

    private final BaseNacosA2ARouter a2aRouter;
    private final ChatClient easyChatClient;
    private final InterruptHandler interruptHandler;
    private final GraphBuilder graphBuilder;
    private final ResilienceManager resilience;

    private final ObjectMapper objectMapper;
    private final PromptManagerBuilder promptManagerBuilder;

    // 固定线程池，替代虚拟线程
    private final ExecutorService parallelExecutor;


    public GraphExecutor(BaseNacosA2ARouter a2aRouter,
                         @Qualifier("fastModelClient") ChatClient easyChatClient,
                         InterruptHandler interruptHandler,
                         GraphBuilder graphBuilder,
                         ResilienceManager resilience,
                         ObjectMapper objectMapper,
                         PromptManagerBuilder promptManagerBuilder) {
        this.a2aRouter = a2aRouter;
        this.easyChatClient = easyChatClient;
        this.interruptHandler = interruptHandler;
        this.graphBuilder = graphBuilder;
        this.resilience = resilience;
        this.objectMapper = objectMapper;
        this.promptManagerBuilder = promptManagerBuilder;
        this.parallelExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }


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
        List<Step> orderedSteps = graphBuilder.buildExecutionOrder(plan);
        Map<String, Object> runtimeContext = new ConcurrentHashMap<>();
        List<ExecutionResult> results = new ArrayList<>();

        for (int i = 0; i < orderedSteps.size(); i++) {
            Step step = orderedSteps.get(i);
            ExecutionResult r = executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx);
            results.add(r);

            // ============ 检查点逻辑 ============
            if (r.getCommand() != null) {
                // 情况1：遇到 INTERRUPT 步骤，创建检查点并暂停
                log.info("触发检查点: stepId={}, command={}", step.getId(), r.getCommand().getType());
                interruptHandler.suspend(
                        xid, userId, threadId, step.getId(),
                        plan, i, runtimeContext, r.getCommand()
                );
                break;  // 暂停执行，等待用户响应
            }

            if (!r.isSuccess() && step.isMandatory()) {
                // 情况2：必选步骤失败，自动触发回滚
                log.error("必选步骤失败，触发自动回滚: stepId={}", step.getId());
                interruptHandler.rollback(xid);
                break;
            }

            // 正常成功，更新运行时上下文
            if (r.isSuccess() && r.getOutput() != null) {
                runtimeContext.put(step.getId() + ".output", r.getOutput());
            }
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
                                executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx),
                        parallelExecutor))
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
            case LLM_CALL -> {
                try {
                    // 1. 构建 Prompt：使用 step 中的 task 或 input
                    String prompt = promptManagerBuilder.buildGraphExecutorLLMStepPrompt(step, context, memoryCtx);

                    // 2. 调用模型（带弹性保护）
                    String llmOutput = resilience.executeWithFullProtection(
                            "llm-step-call",
                            () -> easyChatClient.prompt(prompt).call().content(),
                            () -> "LLM调用降级，返回默认回复"
                    );

                    // 3. 返回结果
                    yield ExecutionResult.builder()
                            .stepId(step.getId())
                            .success(true)
                            .output(Map.of("content", llmOutput))
                            .build();
                } catch (Exception e) {
                    log.error("LLM步骤执行失败: stepId={}", step.getId(), e);
                    yield ExecutionResult.builder()
                            .stepId(step.getId())
                            .success(false)
                            .error("LLM调用失败: " + e.getMessage())
                            .build();
                }
            }
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