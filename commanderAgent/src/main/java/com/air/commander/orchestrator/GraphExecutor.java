package com.air.commander.orchestrator;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.model.*;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
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
        return execute(plan, threadId, userId, tokens, xid, memoryCtx, new ConcurrentHashMap<>());
    }

    public List<ExecutionResult> execute(OrchestrationPlan plan,
                                         String threadId,
                                         String userId,
                                         Map<String, String> tokens,
                                         String xid,
                                         MemoryContext memoryCtx,
                                         Map<String, Object> runtimeContext) {
        return switch (plan.getExecutionMode()) {
            case SEQUENTIAL -> executeSequential(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case PARALLEL -> executeParallel(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case CONDITIONAL -> executeConditional(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case ITERATIVE_CORRECTION -> executeIterative(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case COMPETITIVE -> executeCompetitive(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case PIPELINE -> executePipeline(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
        };
    }


    private List<ExecutionResult> executeSequential(OrchestrationPlan plan,
                                                    String threadId,
                                                    String userId,
                                                    Map<String, String> tokens,
                                                    String xid,
                                                    MemoryContext memoryCtx,
                                                    Map<String, Object> runtimeContext) {
        List<Step> orderedSteps = graphBuilder.buildSequentialExecutionOrder(plan);

        // 注入 userQuery 到全局上下文
        if (memoryCtx != null && memoryCtx.getUserQuery() != null) {
            runtimeContext.put("userQuery", memoryCtx.getUserQuery());
        }

        List<ExecutionResult> results = new ArrayList<>();

        for (int i = 0; i < orderedSteps.size(); i++) {
            Step step = orderedSteps.get(i);
            ExecutionResult r = executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx);
            results.add(r);

            if (!postProcessStepResult(r, step, runtimeContext, plan, i, xid, userId, threadId)) {
                break; // 中断或回滚，停止执行
            }
        }
        return results;
    }

    /**
     * 单步执行后的统一后处理
     * 包含：输出注册、中断检查、失败策略处理
     *
     * @return true = 正常继续，false = 需要停止执行（中断或回滚）
     */
    private boolean postProcessStepResult(ExecutionResult r, Step step,
                                          Map<String, Object> runtimeContext,
                                          OrchestrationPlan plan, int stepIndex,
                                          String xid, String userId, String threadId) {
        // 1. 注册输出（成功时）
        if (r.isSuccess() && r.getOutput() != null) {
            dataContractEngine.publishOutput(step, r, runtimeContext);
        }

        // 2. 检查中断
        if (r.getCommand() != null) {
            log.info("触发检查点: stepId={}, command={}", step.getId(), r.getCommand().getType());
            interruptHandler.suspend(xid, userId, threadId, step.getId(), plan, stepIndex, runtimeContext, r.getCommand());
            return false; // 停止执行
        }

        // 3. 失败策略处理
        if (!r.isSuccess()) {
            StepDataContract.FailurePolicy policy = dataContractEngine.getFailurePolicy(step);
            switch (policy) {
                case ROLLBACK_AND_STOP -> {
                    log.error("必选步骤失败，触发回滚: stepId={}", step.getId());
                    interruptHandler.rollback(xid);
                    return false; // 停止执行
                }
                case SKIP_AND_CONTINUE -> {
                    log.warn("步骤失败但跳过继续: stepId={}", step.getId());
                }
                case MARK_AS_FAILED -> {
                    log.warn("步骤标记为失败: stepId={}", step.getId());
                }
            }
        }

        return true; // 继续执行
    }

    /**对输入数据进行数据引擎处理，即把{step1.output} 这种占位符号，替换成实际的content内容**/
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
                                                  String threadId, String userId,
                                                  Map<String, String> tokens, String xid,
                                                  MemoryContext memoryCtx,
                                                  Map<String, Object> runtimeContext) {
        // 1. 拓扑排序，识别并行组
        List<List<Step>> parallelGroups = graphBuilder.buildParallelExecutionGroups(plan.getSteps());
        List<ExecutionResult> allResults = new ArrayList<>();

        for (List<Step> group : parallelGroups) {
            // 2. 处理组内单步骤
            if (group.size() == 1) {
                Step step = group.get(0);
                ExecutionResult r = executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx);
                allResults.add(r);

                //中断检查+失败回滚通用
                if (!postProcessStepResult(r, step, runtimeContext, plan,
                        allResults.size() - 1, xid, userId, threadId)) {
                    return allResults;
                }

            } else {
                // 3. 并行执行组内步骤
                List<CompletableFuture<ExecutionResult>> futures = group.stream()
                        .map(step -> CompletableFuture.supplyAsync(() ->
                                        executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx),
                                parallelExecutor))
                        .collect(Collectors.toList());

                List<ExecutionResult> groupResults = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());

                allResults.addAll(groupResults);

                // 统一后处理：先更新上下文，再检查中断和失败
                for (ExecutionResult r : groupResults) {
                    Step step = findStepById(plan.getSteps(), r.getStepId());
                    if (!postProcessStepResult(r, step, runtimeContext, plan,
                            allResults.size() - 1, xid, userId, threadId)) {
                        return allResults;
                    }
                }
            }
        }
        return allResults;
    }

    // 辅助方法：根据stepId查找Step
    private Step findStepById(List<Step> steps, String stepId) {
        return steps.stream().filter(s -> s.getId().equals(stepId)).findFirst().orElse(null);
    }

    private List<ExecutionResult> executeConditional(OrchestrationPlan plan,
                                                     String threadId,
                                                     String userId,
                                                     Map<String, String> tokens,
                                                     String xid,
                                                     MemoryContext memoryCtx,
                                                     Map<String, Object> runtimeContext) {
        // todo 暂用顺序执行代替，后续可扩展条件分支逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
    }

    private List<ExecutionResult> executeIterative(OrchestrationPlan plan,
                                                   String threadId,
                                                   String userId,
                                                   Map<String, String> tokens,
                                                   String xid,
                                                   MemoryContext memoryCtx, Map<String, Object> runtimeContext) {
        // todo 暂用顺序执行代替，后续可扩展循环纠正逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
    }

    private List<ExecutionResult> executeCompetitive(OrchestrationPlan plan,
                                                     String threadId,
                                                     String userId,
                                                     Map<String, String> tokens,
                                                     String xid,
                                                     MemoryContext memoryCtx, Map<String, Object> runtimeContext) {
        // todo 暂用顺序执行代替，后续可扩展竞争选择逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
    }

    private List<ExecutionResult> executePipeline(OrchestrationPlan plan,
                                                  String threadId,
                                                  String userId,
                                                  Map<String, String> tokens,
                                                  String xid,
                                                  MemoryContext memoryCtx, Map<String, Object> runtimeContext) {
        // todo 暂用顺序执行代替，后续可扩展流水线传递逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
    }

    private ExecutionResult executeSingleStep(Step step,
                                              Map<String, Object> runtimeContext,
                                              String threadId,
                                              String userId,
                                              Map<String, String> tokens,
                                              String xid,
                                              MemoryContext memoryCtx) {
        // 1. 先对步骤进行数据契约处理，解析输入占位符、注入 userQuery 等
        Step enrichedStep = getEnrichedStep(step, runtimeContext);

        return switch (step.getType()) {
            case A2A_DELEGATE -> doA2ADelegateLogic(enrichedStep, runtimeContext, threadId, tokens, xid, memoryCtx);
            case LLM_CALL -> {
                try {
                    yield doLLMCallLogic(enrichedStep, runtimeContext, memoryCtx);
                } catch (Exception e) {
                    log.error("LLM步骤执行失败: stepId={}", enrichedStep.getId(), e);
                    yield ExecutionResult.builder()
                            .stepId(enrichedStep.getId())
                            .success(false)
                            .error("LLM调用失败: " + e.getMessage())
                            .build();
                }
            }
            case INTERRUPT -> doInterruptLogic(enrichedStep, runtimeContext);
        };
    }

    private ExecutionResult doA2ADelegateLogic(Step step, Map<String, Object> runtimeContext, String threadId, Map<String, String> tokens, String xid, MemoryContext memoryCtx) {
        return a2aRouter.callAgent(step, runtimeContext, tokens, threadId, xid, memoryCtx);
    }

    private ExecutionResult doInterruptLogic(Step step, Map<String, Object> runtimeContext) {
        // 1. 根据中断步骤的依赖关系，精确获取需要展示给用户的前序输出
        Map<String, Object> previewOutput = new HashMap<>();

        if (step.getDependsOn() != null && !step.getDependsOn().isEmpty()) {
            Map<String, Object> previousOutputs = new LinkedHashMap<>();
            for (String dependStepId : step.getDependsOn()) {
                String outputKey = dependStepId + ".output";
                if (runtimeContext.containsKey(outputKey)) {
                    previousOutputs.put(dependStepId, runtimeContext.get(outputKey));
                }
            }
            if (!previousOutputs.isEmpty()) {
                // 如果有多个依赖，使用 previousOutputs；如果只有一个，仍然包装为Map方便前端统一处理
                previewOutput.put("previousStepOutputs", previousOutputs);
            }
        }  else if (!runtimeContext.isEmpty()) {
            // 如果没有显式依赖（向后兼容），尝试取最后一个已完成的步骤
            String lastOutputKey = runtimeContext.keySet().stream()
                    .filter(k -> k.endsWith(".output"))
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (lastOutputKey != null) {
                previewOutput.put("previousStepOutput", runtimeContext.get(lastOutputKey));
            }
        }

        // 2. 构建中断命令（保持不变）
        ExecutionResult.Command.CommandBuilder commandBuilder = ExecutionResult.Command.builder();
        if (step.getCheckpoint() != null) {
            Step.CheckpointConfig cp = step.getCheckpoint();
            String commandType = cp.getType() == Step.CheckpointConfig.CheckpointType.CREDENTIAL
                    ? "REQUEST_CREDENTIAL" : "REQUEST_CONFIRM";
            commandBuilder.type(commandType)
                    .message(cp.getQuestion() != null ? cp.getQuestion() : step.getQuestion())
                    .requiredScopes(cp.getRequiredScopes() != null ? cp.getRequiredScopes() : List.of());
        } else {
            commandBuilder.type("REQUEST_CONFIRM")
                    .message(step.getQuestion())
                    .requiredScopes(List.of());
        }
        previewOutput.put("question", commandBuilder.build().getMessage());

        // 3. 返回结果
        return ExecutionResult.builder()
                .stepId(step.getId())
                .success(false)
                .command(commandBuilder.build())
                .output(previewOutput)
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