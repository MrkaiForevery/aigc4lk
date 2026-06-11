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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ConditionalExecutor {

    private final GraphBuilder graphBuilder;
    private final StepUnitExecutor stepUnitExecutor;
    private final GraphCommonDataProcessor graphCommonDataProcessor;

    public ConditionalExecutor(GraphBuilder graphBuilder,
                               StepUnitExecutor stepUnitExecutor,
                               GraphCommonDataProcessor graphCommonDataProcessor) {
        this.graphBuilder = graphBuilder;
        this.stepUnitExecutor = stepUnitExecutor;
        this.graphCommonDataProcessor = graphCommonDataProcessor;
    }

    /**
     * 条件分支执行模式
     * 支持基于LLM分析结果进行多路分支跳转
     */
    public List<ExecutionResult> executeConditional(OrchestrationPlan plan,
                                                    String threadId, String userId,
                                                    Map<String, String> tokens, String xid,
                                                    MemoryContext memoryCtx,
                                                    Map<String, Object> runtimeContext) {
        if (memoryCtx != null && memoryCtx.getUserQuery() != null) {
            runtimeContext.put("userQuery", memoryCtx.getUserQuery());
        }

        List<Step> orderedSteps = graphBuilder.buildSequentialExecutionOrder(plan);
        List<ExecutionResult> allResults = new ArrayList<>();

        int currentIndex = 0;
        int maxSteps = orderedSteps.size() * 2;
        int stepCount = 0;

        while (currentIndex < orderedSteps.size() && stepCount < maxSteps) {
            stepCount++;
            Step step = orderedSteps.get(currentIndex);

            // 跳过已完成的步骤（恢复checkPoint时使用）
            if (runtimeContext.containsKey(step.getId() + ".output") ||
                    runtimeContext.containsKey(step.getId() + ".interrupted")) {
                currentIndex++;
                continue;
            }

            // 执行当前步骤（内部已包含数据契约处理）
            ExecutionResult r = stepUnitExecutor.executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx);
            allResults.add(r);

            // ========== 条件分支核心逻辑 ==========
            if (isConditionStep(step)) {
                // 先通过统一后处理注册输出
                boolean shouldContinue = graphCommonDataProcessor.postProcessStepResult(r, step, runtimeContext, plan,
                        allResults.size() - 1, xid, userId, threadId);
                if (!shouldContinue) return allResults;

                String branchLabel = extractBranchLabel(r);
                log.info("条件判断结果: stepId={}, branchLabel={}", step.getId(), branchLabel);
                Map<String, String> branches = parseBranches(step);
                String targetStepId = branches.getOrDefault(branchLabel, branches.get("default"));

                if (targetStepId != null) {
                    int targetIndex = graphCommonDataProcessor.findStepIndex(orderedSteps, targetStepId);
                    if (targetIndex >= 0) {
                        currentIndex = targetIndex; // 跳转
                        continue;
                    }
                }
                // 没有目标步骤或目标不存在，继续顺序执行下一步
                currentIndex++;
                continue;
            }

            // ========== 非条件步骤：统一后处理 ==========
            boolean shouldContinue = graphCommonDataProcessor.postProcessStepResult(r, step, runtimeContext, plan,
                    allResults.size() - 1, xid, userId, threadId);
            if (!shouldContinue) {
                return allResults; // 中断或回滚
            }

            currentIndex++;
        }

        return allResults;
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
     * 从 LLM 输出中提取分支标签
     * LLM 应该被引导输出简短的分支标签（如 "true", "false", "high", "low" 等）
     */
    private String extractBranchLabel(ExecutionResult r) {
        if (r.isSuccess() && r.getOutput() != null) {
            Object content = r.getOutput().get("content");
            if (content != null) {
                // LLM 的输出可能包含多余空格或换行，清理一下
                return content.toString().trim().toLowerCase();
            }
        }
        return "default"; // 降级到默认分支
    }

    /**
     * 从 step.input 中解析分支映射表
     * 例如：
     * {
     * "branches": {
     * "下滑": "step_fix",
     * "平稳": "step_summary",
     * "增长": "step_expand"
     * },
     * "default": "step_summary"
     * }
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseBranches(Step step) {
        Map<String, Object> input = step.getInput();
        if (input != null && input.containsKey("branches")) {
            Map<String, String> branches = new HashMap<>();
            Map<String, Object> rawBranches = (Map<String, Object>) input.get("branches");
            for (Map.Entry<String, Object> entry : rawBranches.entrySet()) {
                branches.put(entry.getKey().toLowerCase(), entry.getValue().toString());
            }
            // 提取默认分支
            if (input.containsKey("default")) {
                branches.put("default", input.get("default").toString());
            }
            return branches;
        }
        return Map.of();
    }
}
