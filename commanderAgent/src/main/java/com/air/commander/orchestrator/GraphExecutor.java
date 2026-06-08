package com.air.commander.orchestrator;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.model.*;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private final PromptManagerBuilder promptManagerBuilder;
    private final DataContractEngine dataContractEngine;

    // 固定线程池，替代虚拟线程
    private final ExecutorService parallelExecutor;


    public GraphExecutor(BaseNacosA2ARouter a2aRouter,
                         @Qualifier("fastModelClient") ChatClient easyChatClient,
                         InterruptHandler interruptHandler,
                         GraphBuilder graphBuilder,
                         ResilienceManager resilience,
                         PromptManagerBuilder promptManagerBuilder,
                         DataContractEngine dataContractEngine) {
        this.a2aRouter = a2aRouter;
        this.easyChatClient = easyChatClient;
        this.interruptHandler = interruptHandler;
        this.graphBuilder = graphBuilder;
        this.resilience = resilience;
        this.promptManagerBuilder = promptManagerBuilder;
        this.dataContractEngine = dataContractEngine;
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

        // 注入 userQuery 到全局上下文
        if (memoryCtx != null && memoryCtx.getUserQuery() != null) {
            runtimeContext.put("userQuery", memoryCtx.getUserQuery());
        }

        List<ExecutionResult> results = new ArrayList<>();

        for (int i = 0; i < orderedSteps.size(); i++) {
            Step step = orderedSteps.get(i);
            //获取通过数据引擎处理过的Step
            Step enrichedStep = getEnrichedStep(step, runtimeContext);

            ExecutionResult r = executeSingleStep(enrichedStep, runtimeContext, threadId, userId, tokens, xid, memoryCtx);
            results.add(r);

            // 使用 DataContractEngine 决定失败策略
            StepDataContract.FailurePolicy policy = dataContractEngine.getFailurePolicy(step);

            // ============ 检查点逻辑 ============
            if (r.getCommand() != null) {
                // 情况1：遇到 INTERRUPT 步骤，创建检查点并暂停
                log.info("触发检查点: stepId={}, command={}", step.getId(), r.getCommand().getType());
                interruptHandler.suspend(xid, userId, threadId, step.getId(), plan, i, runtimeContext, r.getCommand()
                );
                break;  // 暂停执行，等待用户响应
            }

            if (!r.isSuccess()) {
                switch (policy) {
                    case ROLLBACK_AND_STOP -> {
                        log.error("必选步骤失败，触发回滚: stepId={}", step.getId());
                        interruptHandler.rollback(xid);
                        return results;
                    }
                    case SKIP_AND_CONTINUE -> {
                        log.warn("步骤失败但跳过继续: stepId={}", step.getId());
                    }
                    case MARK_AS_FAILED -> {
                        log.warn("步骤标记为失败: stepId={}", step.getId());
                    }
                }
            }

            // 使用 DataContractEngine 注册输出
            if (r.isSuccess() && r.getOutput() != null) {
                dataContractEngine.publishOutput(step, r, runtimeContext);
            }
        }
        return results;
    }

    private Step getEnrichedStep(Step step, Map<String, Object> runtimeContext) {
        // 使用 DataContractEngine 构建输入
        Map<String, Object> stepInput = dataContractEngine.buildInput(step, runtimeContext);
        // 将构建好的输入注入到 step 中（覆盖原有 input）
        Step enrichedStep = Step.builder()
                .id(step.getId())
                .type(step.getType())
                .agent(step.getAgent())
                .task(step.getTask())
                .input(stepInput)
                .dependsOn(step.getDependsOn())
                .mandatory(step.isMandatory())
                .checkpoint(step.getCheckpoint())
                .dataContract(step.getDataContract())
                .build();
        return enrichedStep;
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
                                              Map<String, Object> runtimeContext,
                                              String threadId,
                                              String userId,
                                              Map<String, String> tokens,
                                              String xid,
                                              MemoryContext memoryCtx) {
        return switch (step.getType()) {
            case A2A_DELEGATE -> doA2ADelegateLogic(step, runtimeContext, threadId, tokens, xid, memoryCtx);
            case LLM_CALL -> {
                try {
                    yield doLLMCallLogic(step, runtimeContext, memoryCtx);
                } catch (Exception e) {
                    log.error("LLM步骤执行失败: stepId={}", step.getId(), e);
                    yield ExecutionResult.builder()
                            .stepId(step.getId())
                            .success(false)
                            .error("LLM调用失败: " + e.getMessage())
                            .build();
                }
            }
            case INTERRUPT -> doInterruptLogic(step, runtimeContext);
        };
    }

    private ExecutionResult doA2ADelegateLogic(Step step, Map<String, Object> runtimeContext, String threadId, Map<String, String> tokens, String xid, MemoryContext memoryCtx) {
        return a2aRouter.callAgent(step, runtimeContext, tokens, threadId, xid, memoryCtx);
    }

    private ExecutionResult doInterruptLogic(Step step, Map<String, Object> runtimeContext) {
        // 1. 收集前序步骤的最新输出（供用户参考）
        Map<String, Object> previewOutput = new HashMap<>();
        if (!runtimeContext.isEmpty()) {
            // 取最后一个步骤的输出（假设 context 的 key 是 stepId.output 格式）
            String lastOutputKey = runtimeContext.keySet().stream()
                    .filter(k -> k.endsWith(".output"))
                    .reduce((first, second) -> second) // 取最后一个
                    .orElse(null);
            if (lastOutputKey != null) {
                previewOutput.put("previousStepOutput", runtimeContext.get(lastOutputKey));
            }
        }

        // 2. 构建中断命令
        ExecutionResult.Command.CommandBuilder commandBuilder = ExecutionResult.Command.builder();
        if (step.getCheckpoint() != null) {
            // 使用 CheckpointConfig
            Step.CheckpointConfig cp = step.getCheckpoint();
            String commandType = cp.getType() == Step.CheckpointConfig.CheckpointType.CREDENTIAL
                    ? "REQUEST_CREDENTIAL" : "REQUEST_CONFIRM";
            commandBuilder.type(commandType)
                    .message(cp.getQuestion() != null ? cp.getQuestion() : step.getQuestion())
                    .requiredScopes(cp.getRequiredScopes() != null ? cp.getRequiredScopes() : List.of());
        } else {
            // 兼容旧版简单中断
            commandBuilder.type("REQUEST_CONFIRM")
                    .message(step.getQuestion())
                    .requiredScopes(List.of());
        }
        previewOutput.put("question", commandBuilder.build().getMessage()); // 也把问题本身放入输出

        // 3. 返回“等待用户操作”的结果
        return ExecutionResult.builder()
                .stepId(step.getId())
                .success(false)          // 步骤未完成，但并非错误
                .command(commandBuilder.build())
                .output(previewOutput)   // 携带前序输出供前端展示
                .build();
    }

    private ExecutionResult doLLMCallLogic(Step step, Map<String, Object> runtimeContext, MemoryContext memoryCtx) {
        // 1. 构建 Prompt：使用 step 中的 task 或 input
        String prompt = null;
        try {
            prompt = promptManagerBuilder.buildGraphExecutorLLMStepPrompt(step, runtimeContext, memoryCtx);
        } catch (JsonProcessingException e) {
            log.error("构建LLMCall的prompt提示词失败！！");
            throw new RuntimeException(e);
        }

        // 2. 调用模型（带弹性保护）
        String finalPrompt = prompt;
        long startTime = System.currentTimeMillis();
        String llmOutput = resilience.executeWithFullProtection(
                "llm-step-call",
                () -> easyChatClient.prompt(finalPrompt).call().content(),
                () -> "LLM调用降级，返回默认回复"
        );

        long endTime = System.currentTimeMillis();

        // 3. 返回结果
        return ExecutionResult.builder()
                .stepId(step.getId())
                .success(true)
                .output(Map.of("content", llmOutput))
                .durationMs(endTime-startTime)
                .build();
    }
}