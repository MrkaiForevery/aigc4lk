package com.air.commander.graph.executor;

import com.air.commander.graph.GraphBuilder;
import com.air.commander.graph.common.GraphCommonDataProcessor;
import com.air.commander.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 循环纠正模式执行器
 */
@Slf4j
@Component
public class IterativeCorrectionExecutor {

    private final GraphBuilder graphBuilder;
    private final SequentialExecutor sequentialExecutor;
    private final StepUnitExecutor stepUnitExecutor;
    private final GraphCommonDataProcessor graphCommonDataProcessor;

    private final ObjectMapper objectMapper;

    public IterativeCorrectionExecutor(GraphBuilder graphBuilder,
                                       SequentialExecutor sequentialExecutor,
                                       StepUnitExecutor stepUnitExecutor,
                                       GraphCommonDataProcessor graphCommonDataProcessor,
                                       ObjectMapper objectMapper) {
        this.graphBuilder = graphBuilder;
        this.sequentialExecutor = sequentialExecutor;
        this.stepUnitExecutor = stepUnitExecutor;
        this.graphCommonDataProcessor = graphCommonDataProcessor;
        this.objectMapper = objectMapper;
    }

    /**
     * 循环-纠正执行模式
     * 支持插入checkPoint，支持多步骤循环
     */
    /**
     * 循环纠正执行模式（支持多循环闭环）
     */
    public List<ExecutionResult> executeIterativeCorrection(OrchestrationPlan plan,
                                                             String threadId, String userId,
                                                             Map<String, String> tokens, String xid,
                                                             MemoryContext memoryCtx,
                                                             Map<String, Object> runtimeContext) {
        log.info("以IterativeCorrection模式开始执行任务..... ");

        long start= System.currentTimeMillis();

        if (memoryCtx != null && memoryCtx.getUserQuery() != null) {
            runtimeContext.put("userQuery", memoryCtx.getUserQuery());
        }

        OrchestrationPlan.CorrectionConfig config = plan.getCorrectionConfig();
        if (config == null || config.getLoops() == null || config.getLoops().isEmpty()) {
            log.warn("ITERATIVE_CORRECTION 缺少有效的 correctionConfig，降级为顺序执行");
            return sequentialExecutor.executeSequential(plan, threadId, userId, tokens, xid, memoryCtx, runtimeContext);
        }

        List<Step> orderedAll = graphBuilder.buildSequentialExecutionOrder(plan);
        Map<String, Object> context = new ConcurrentHashMap<>(runtimeContext);
        List<ExecutionResult> allResults = new ArrayList<>();

        int currentStepIndex = 0;

        // 遍历每个循环闭环
        for (OrchestrationPlan.CorrectionLoop loop : config.getLoops()) {
            // 1. 执行当前循环之前的普通步骤
            List<Step> preLoopSteps = getStepsUpTo(orderedAll, currentStepIndex, loop.getFirstStepId());
            for (Step step : preLoopSteps) {
                if (graphCommonDataProcessor.isStepCompleted(step, context)) continue;
                ExecutionResult r = stepUnitExecutor.executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx);
                allResults.add(r);
                if (!graphCommonDataProcessor.postProcessStepResult(r, step, context, plan, allResults.size() - 1, xid, userId, threadId)) {
                    return allResults;
                }
            }

            // 2. 执行单个循环闭环
            List<ExecutionResult> loopResults = executeSingleLoop(plan, loop, orderedAll, context,
                    threadId, userId, tokens, xid, memoryCtx);
            allResults.addAll(loopResults);

            // 检查是否在循环中被中断
            if (loopResults.stream().anyMatch(r -> r.getCommand() != null)) {
                return allResults;
            }

            // 3. 将索引移动到评估步骤之后
            currentStepIndex = graphCommonDataProcessor.findStepIndex(orderedAll, loop.getEvaluatorStepId()) + 1;
        }

        // 4. 执行最后一个循环之后的剩余步骤
        List<Step> postLoopSteps = orderedAll.subList(currentStepIndex, orderedAll.size());
        for (Step step : postLoopSteps) {
            if (graphCommonDataProcessor.isStepCompleted(step, context)) continue;
            ExecutionResult r = stepUnitExecutor.executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx);
            allResults.add(r);
            if (!graphCommonDataProcessor.postProcessStepResult(r, step, context, plan, allResults.size() - 1, xid, userId, threadId)) {
                return allResults;
            }
        }

        long end = System.currentTimeMillis();
        log.info("以IterativeCorrection模式执行任务结束，本次耗时:{}ms ",end-start);

        return allResults;
    }


    // 获取从 startIndex 到 targetStepId 之间的步骤（不含 targetStepId）
    private List<Step> getStepsUpTo(List<Step> orderedSteps, int startIndex, String targetStepId) {
        List<Step> steps = new ArrayList<>();
        for (int i = startIndex; i < orderedSteps.size(); i++) {
            if (orderedSteps.get(i).getId().equals(targetStepId)) break;
            steps.add(orderedSteps.get(i));
        }
        return steps;
    }

    /**
     * 执行单个循环闭环
     */
    private List<ExecutionResult> executeSingleLoop(OrchestrationPlan plan, OrchestrationPlan.CorrectionLoop loop,
                                                    List<Step> orderedAll, Map<String, Object> context,
                                                    String threadId, String userId, Map<String, String> tokens,
                                                    String xid, MemoryContext memoryCtx) {
        List<ExecutionResult> results = new ArrayList<>();

        // 确定循环体主步骤：firstStepId 到 evaluatorStepId 之间（不含 evaluatorStep）
        Step evaluatorStep = graphCommonDataProcessor.findStepById(orderedAll, loop.getEvaluatorStepId());
        Step correctorStep = loop.getCorrectorStepId() != null ?
                graphCommonDataProcessor.findStepById(orderedAll, loop.getCorrectorStepId()) : null;

        int firstIdx =graphCommonDataProcessor.findStepIndex(orderedAll, loop.getFirstStepId());
        int evalIdx = graphCommonDataProcessor.findStepIndex(orderedAll, evaluatorStep.getId());
        List<Step> mainSteps = orderedAll.subList(firstIdx, evalIdx);

        // 恢复循环状态
        IterationState state = loadIterationState(context, loop.getLoopId());
        int iteration = state != null ? state.getIteration() : 0;
        String phase = state != null ? state.getPhase() : "MAIN";
        int mainIndex = state != null ? state.getMainStepIndex() : 0;

        while (iteration < loop.getMaxIterations()) {
            log.debug("循环 [{}] 第 {} 轮，阶段: {}", loop.getLoopId(), iteration + 1, phase);

            // 执行主步骤序列（仅执行尚未完成的）
            if ("MAIN".equals(phase)) {
                for (int i = mainIndex; i < mainSteps.size(); i++) {
                    Step step = mainSteps.get(i);
                    if (graphCommonDataProcessor.isStepCompleted(step, context)) continue;

                    ExecutionResult r = stepUnitExecutor.executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx);
                    results.add(r);

                    if (!graphCommonDataProcessor.postProcessStepResult(r, step, context, plan, results.size() - 1, xid, userId, threadId)) {
                        // 检查点中断：保存状态后返回
                        if (r.getCommand() != null) {
                            saveIterationState(context, loop.getLoopId(), iteration, "MAIN", i);
                        }
                        return results;
                    }
                }
                mainIndex = 0;
            }

            // 执行评估步骤
            if ("EVALUATE".equals(phase) || "MAIN".equals(phase)) {
                if (!graphCommonDataProcessor.isStepCompleted(evaluatorStep, context)) {
                    ExecutionResult evalR = stepUnitExecutor.executeSingleStep(evaluatorStep, context, threadId, userId, tokens, xid, memoryCtx);
                    results.add(evalR);

                    if (!graphCommonDataProcessor.postProcessStepResult(evalR, evaluatorStep, context, plan, results.size() - 1, xid, userId, threadId)) {
                        if (evalR.getCommand() != null) {
                            saveIterationState(context, loop.getLoopId(), iteration, "EVALUATE", 0);
                        }
                        return results;
                    }
                }

                int score = extractQualityScore(evaluatorStep, context);
                if (score >= loop.getQualityThreshold()) {
                    log.info("循环 [{}] 达标，score={}, threshold={}", loop.getLoopId(), score, loop.getQualityThreshold());
                    break;
                }
                log.info("循环 [{}] 未达标，score={}, threshold={}", loop.getLoopId(), score, loop.getQualityThreshold());
            }

            // 执行修正步骤（如果存在）
            if (correctorStep != null && ("CORRECT".equals(phase) || "EVALUATE".equals(phase) || "MAIN".equals(phase))) {
                if (!graphCommonDataProcessor.isStepCompleted(correctorStep, context)) {
                    ExecutionResult correctR = stepUnitExecutor.executeSingleStep(correctorStep, context, threadId, userId, tokens, xid, memoryCtx);
                    results.add(correctR);

                    if (!graphCommonDataProcessor.postProcessStepResult(correctR, correctorStep, context, plan, results.size() - 1, xid, userId, threadId)) {
                        if (correctR.getCommand() != null) {
                            saveIterationState(context, loop.getLoopId(), iteration, "CORRECT", 0);
                        }
                        return results;
                    }
                }
            }

            iteration++;
            phase = "MAIN";
            saveIterationState(context, loop.getLoopId(), iteration, "MAIN", 0);
        }

        // 达到最大迭代次数且需要确认
        if (iteration >= loop.getMaxIterations() && loop.isCheckpointOnMaxIterations()) {
            int finalScore = extractQualityScore(evaluatorStep, context);
            ExecutionResult timeoutResult = ExecutionResult.builder()
                    .stepId("max_iterations_reached_" + loop.getLoopId())
                    .success(false)
                    .command(ExecutionResult.Command.builder()
                            .type("REQUEST_CONFIRM")
                            .message(String.format("循环 [%s] 已达最大迭代次数 %d，当前评分 %d 未达标。是否接受当前结果？",
                                    loop.getLoopId(), loop.getMaxIterations(), finalScore))
                            .requiredScopes(List.of())
                            .build())
                    .output(Map.of("currentScore", finalScore))
                    .build();
            results.add(timeoutResult);
        }

        return results;
    }

    private IterationState loadIterationState(Map<String, Object> context, String loopId) {
        Object stateObj = context.get("iterationState_" + loopId);
        if (stateObj == null) return null;
        try {
            Map<String, Object> state = objectMapper.readValue(stateObj.toString(), Map.class);
            IterationState is = new IterationState();
            is.setIteration(((Number) state.get("iteration")).intValue());
            is.setPhase((String) state.get("phase"));
            is.setMainStepIndex(((Number) state.get("mainStepIndex")).intValue());
            return is;
        } catch (Exception e) {
            log.error("加载循环状态失败", e);
            return null;
        }
    }

    // 循环状态保存/恢复（存储在 runtimeContext 中）
    private void saveIterationState(Map<String, Object> context, String loopId,
                                    int iteration, String phase, int mainStepIndex) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("iteration", iteration);
            state.put("phase", phase);
            state.put("mainStepIndex", mainStepIndex);
            context.put("iterationState_" + loopId, objectMapper.writeValueAsString(state));
        } catch (JsonProcessingException e) {
            log.error("保存循环状态失败", e);
        }
    }


    // 从评估步骤的输出中提取评分
    private int extractQualityScore(Step evaluatorStep, Map<String, Object> context) {
        Object output = context.get(evaluatorStep.getId() + ".output");
        if (output instanceof Map) {
            Object content = ((Map<?, ?>) output).get("content");
            if (content instanceof String) {
                // 尝试解析 JSON 中的 score 字段
                try {
                    Map<String, Object> scoreMap = objectMapper.readValue((String) content, Map.class);
                    if (scoreMap.containsKey("score")) {
                        return ((Number) scoreMap.get("score")).intValue();
                    }
                } catch (Exception e) {
                    // 如果不是 JSON，尝试从文本中提取数字
                    String scoreStr = ((String) content).replaceAll("[^0-9]", "");
                    if (!scoreStr.isEmpty()) {
                        return Integer.parseInt(scoreStr);
                    }
                }
            }
        }
        return 0;
    }


}
