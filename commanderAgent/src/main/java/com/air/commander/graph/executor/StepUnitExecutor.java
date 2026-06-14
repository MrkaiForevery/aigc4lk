package com.air.commander.graph.executor;

import com.air.commander.Prompt.PromptManagerBuilder;
import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.chat.ChatClientSelector;
import com.air.commander.conversation.contract.DataContractEngine;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.Step;
import com.air.commander.model.StepDataContract;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class StepUnitExecutor {

    private final ChatClientSelector chatClientSelector;
    private final BaseNacosA2ARouter a2aRouter;

    private final PromptManagerBuilder promptManagerBuilder;
    private final ResilienceManager resilience;
    private final DataContractEngine dataContractEngine;

    private final ObjectMapper objectMapper;

    public StepUnitExecutor(ChatClientSelector chatClientSelector,
                            BaseNacosA2ARouter a2aRouter,
                            PromptManagerBuilder promptManagerBuilder,
                            ResilienceManager resilience,
                            DataContractEngine dataContractEngine,
                            ObjectMapper objectMapper) {
        this.chatClientSelector = chatClientSelector;
        this.a2aRouter = a2aRouter;
        this.promptManagerBuilder = promptManagerBuilder;
        this.resilience = resilience;
        this.dataContractEngine = dataContractEngine;
        this.objectMapper = objectMapper;
    }

    public ExecutionResult executeSingleStep(Step step,
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

    /**
     * 执行 A2A 委托调用
     * 包含循环模式-纠正步骤提示词定制化处理，
     * 包含竞争模式-评审步骤提示定制化处理
     * 正常模式也统一处理
     */
    private ExecutionResult doA2ADelegateLogic(Step step, Map<String, Object> runtimeContext,
                                               String threadId, Map<String, String> tokens,
                                               String xid, MemoryContext memoryCtx) {
        String prompt = getAppropriatePromptByStepFlag(step, runtimeContext, memoryCtx);
        step.setPreBuiltA2AAgentContent(prompt);
        return a2aRouter.callAgent(step, tokens, threadId, xid, memoryCtx);
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
        } else if (!runtimeContext.isEmpty()) {
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

        // 1. 构建 Prompt
        String prompt = getAppropriatePromptByStepFlag(step,runtimeContext,memoryCtx);

        // 2. 调用模型（带弹性保护）
        long startTime = System.currentTimeMillis();
        ChatClient chatClient = chatClientSelector.getClient(step.getModel());
        String llmOutput = resilience.executeWithFullProtection(
                "llm-step-call",
                () -> chatClient.prompt(prompt).call().content(),
                () -> "LLM调用降级，返回默认回复"
        );

        long endTime = System.currentTimeMillis();

        // 3. 返回结果
        return ExecutionResult.builder()
                .stepId(step.getId())
                .success(true)
                .output(Map.of("content", llmOutput))
                .durationMs(endTime - startTime)
                .build();
    }

    /**
     * 对输入数据进行数据引擎处理，即把{step1.output} 这种占位符号，替换成实际的content内容
     **/
    private Step getEnrichedStep(Step step, Map<String, Object> runtimeContext) {
        // 使用 DataContractEngine 构建输入
        Map<String, Object> stepInput = dataContractEngine.buildInput(step, runtimeContext);
        // 将构建好的输入注入到 step 中（覆盖原有 input）
        Step enrichedStep = Step.builder()
                .id(step.getId())
                .type(step.getType())
                .model(step.getModel())
                .agent(step.getAgent())
                .task(step.getTask())
                .input(stepInput)
                .dependsOn(step.getDependsOn())
                .mandatory(step.isMandatory())
                .checkpoint(step.getCheckpoint())
                .dataContract(step.getDataContract())
                .competitiveSelectorStepFlag(step.isCompetitiveSelectorStepFlag())
                .iterativeCorrectionStepFlag(step.isIterativeCorrectionStepFlag())
                .build();

        // 自动为条件步骤设置失败策略，避免条件判断失败触发回滚
        if (isConditionStep(enrichedStep) && enrichedStep.getDataContract() == null) {
            enrichedStep.setDataContract(StepDataContract.builder()
                    .onFailure(StepDataContract.FailurePolicy.MARK_AS_FAILED)
                    .build());
        }

        return enrichedStep;
    }

    /**
     * 判断当前步骤是否为条件判断步骤
     * 条件步骤的特征：type = LLM_CALL，且 input 中包含 branches 字段
     */
    private boolean isConditionStep(Step step) {
        return step.getType() == Step.StepType.LLM_CALL
                && step.getInput() != null
                && step.getInput().containsKey("branches");
    }

    /**
     *根据各种step里面预设的标识，判定使用哪种方式构建提示词
     */
    private String getAppropriatePromptByStepFlag(Step step, Map<String, Object> runtimeContext, MemoryContext memoryCtx) {
        try {
            if (step.isIterativeCorrectionStepFlag()) {
                return promptManagerBuilder.buildCorrectionStepPrompt(step, runtimeContext, memoryCtx);
            }
            if (step.isCompetitiveSelectorStepFlag()) {
                return promptManagerBuilder.buildCompetitionJudgeStepPrompt(step, runtimeContext, memoryCtx);
            }
            return promptManagerBuilder.buildGraphExecutorLLMStepPrompt(step, runtimeContext, memoryCtx);
        } catch (JsonProcessingException e) {
            log.error("构建LLMCall的prompt提示词失败！！");
            throw new RuntimeException(e);
        }
    }
}
