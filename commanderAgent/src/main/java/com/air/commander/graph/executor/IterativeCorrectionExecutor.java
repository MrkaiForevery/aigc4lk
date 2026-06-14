package com.air.commander.graph.executor;

import com.air.commander.graph.GraphBuilder;
import com.air.commander.graph.common.GraphCommonDataProcessor;
import com.air.commander.model.*;
import com.air.commander.tools.TextFormatTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
     * 循环纠正执行模式（支持多循环闭环）
     */
    public List<ExecutionResult> executeIterativeCorrection(OrchestrationPlan plan,
                                                            String threadId, String userId,
                                                            Map<String, String> tokens, String xid,
                                                            MemoryContext memoryCtx,
                                                            Map<String, Object> runtimeContext) {
        log.info("以IterativeCorrection模式开始执行任务..... ");
        long start = System.currentTimeMillis();

        if (memoryCtx != null && memoryCtx.getUserQuery() != null) {
            runtimeContext.put("userQuery", memoryCtx.getUserQuery());
        }

        OrchestrationPlan.CorrectionConfig config = plan.getCorrectionConfig();
        if (config == null || config.getLoops() == null || config.getLoops().isEmpty()) {
            log.warn("ITERATIVE_CORRECTION 缺少有效的 correctionConfig，降级为顺序执行");
            return sequentialExecutor.executeSequential(plan, threadId, userId, tokens, xid, memoryCtx, runtimeContext);
        }

        List<Step> orderedAllSteps = graphBuilder.buildSequentialExecutionOrder(plan);
        Map<String, Object> context = new ConcurrentHashMap<>(runtimeContext);
        List<ExecutionResult> allResults = new ArrayList<>();

        int idx = 0;
        while (idx < orderedAllSteps.size()) {
            Step currentStep = orderedAllSteps.get(idx);

            // 如果该步骤已经完成（例如中断恢复后），直接跳过
            if (graphCommonDataProcessor.isStepCompleted(currentStep, context)) {
                idx++;
                continue;
            }

            // 检查当前步骤是否为某个迭代循环的起点
            OrchestrationPlan.CorrectionLoop loop = findLoopByFirstStepId(config, currentStep.getId());

            if (loop != null) {
                // ========== 进入迭代纠正循环体 ==========
                // 1. 执行主步骤（仅一次）
                Step firstStep = currentStep; // 就是当前步骤
                if (!graphCommonDataProcessor.isStepCompleted(firstStep, context)) {
                    ExecutionResult mainResult = stepUnitExecutor.executeSingleStep(
                            firstStep, context, threadId, userId, tokens, xid, memoryCtx);
                    allResults.add(mainResult);
                    if (!graphCommonDataProcessor.postProcessStepResult(mainResult, firstStep, context, plan, allResults.size() - 1, xid, userId, threadId)) {
                        // 主步骤中断（例如 REQUEST_CONFIRM）
                        return allResults;
                    }
                }

                // 2. 执行评估-纠正循环（内部会多次执行 evaluator 和 corrector）
                List<ExecutionResult> loopResults = executeSingleLoop(plan, loop, orderedAllSteps, context, threadId, userId, tokens, xid, memoryCtx);
                allResults.addAll(loopResults);

                // 3. 如果循环过程中本身定义了中断step步骤，那么返回的result里面必然会携带中断执行挂起后返回的结果，外层通过捕获该结果，进行立即中断返回
                if (loopResults.stream().anyMatch(r -> r.getCommand() != null)) {
                    return allResults;
                }

                // 4. 将外层指针移动到循环涉及的最后一步之后
                //    循环涉及的步骤：firstStepId, evaluatorStepId, correctorStepId（若存在）
                int firstIdx = graphCommonDataProcessor.findStepIndex(orderedAllSteps, loop.getFirstStepId());
                int evalIdx = graphCommonDataProcessor.findStepIndex(orderedAllSteps, loop.getEvaluatorStepId());
                int corrIdx = loop.getCorrectorStepId() != null ?
                        graphCommonDataProcessor.findStepIndex(orderedAllSteps, loop.getCorrectorStepId()) : -1;
                int maxLoopIdx = Math.max(firstIdx, Math.max(evalIdx, corrIdx));
                idx = maxLoopIdx + 1;

            } else {
                // ========== 普通步骤，直接执行 ==========
                ExecutionResult r = stepUnitExecutor.executeSingleStep(
                        currentStep, context, threadId, userId, tokens, xid, memoryCtx);
                allResults.add(r);
                if (!graphCommonDataProcessor.postProcessStepResult(
                        r, currentStep, context, plan, allResults.size() - 1, xid, userId, threadId)) {
                    return allResults; // 中断（可能是 INTERRUPT 步骤产生 REQUEST_CONFIRM）
                }
                idx++;
            }
        }

        long end = System.currentTimeMillis();
        log.info("以IterativeCorrection模式执行任务结束，本次耗时:{}ms ", end - start);
        return allResults;
    }


    /**
     * 从 CorrectionConfig 中查找以指定步骤 ID 为起点的循环配置
     */
    private OrchestrationPlan.CorrectionLoop findLoopByFirstStepId(OrchestrationPlan.CorrectionConfig config,
                                                                   String stepId) {
        if (config == null || config.getLoops() == null) return null;
        for (OrchestrationPlan.CorrectionLoop loop : config.getLoops()) {
            if (loop.getFirstStepId().equals(stepId)) {
                return loop;
            }
        }
        return null;
    }


    /**
     * 执行单个循环闭环（仅包含评估和纠正，主步骤已在外部执行）
     */
    private List<ExecutionResult> executeSingleLoop(OrchestrationPlan plan, OrchestrationPlan.CorrectionLoop loop,
                                                    List<Step> orderedAll, Map<String, Object> context,
                                                    String threadId, String userId, Map<String, String> tokens,
                                                    String xid, MemoryContext memoryCtx) {
        List<ExecutionResult> results = new ArrayList<>();

        Step evaluatorStep = graphCommonDataProcessor.findStepById(orderedAll, loop.getEvaluatorStepId());
        Step correctorStep = loop.getCorrectorStepId() != null ?
                graphCommonDataProcessor.findStepById(orderedAll, loop.getCorrectorStepId()) : null;

        // 恢复循环状态（不再有 MAIN 阶段）
        IterationState state = loadIterationState(context, loop.getLoopId());
        int iteration = state != null ? state.getIteration() : 0;
        String phase = state != null ? state.getPhase() : "EVALUATE"; // 默认从评估开始

        while (iteration < loop.getMaxIterations()) {
            log.debug("循环 [{}] 第 {} 轮，阶段: {}", loop.getLoopId(), iteration + 1, phase);

            // 执行评估步骤
            if ("EVALUATE".equals(phase)) {
                // 清除上一次评估的输出，确保每次循环都重新评估
                context.remove(evaluatorStep.getId() + ".output");
                if (!graphCommonDataProcessor.isStepCompleted(evaluatorStep, context)) {
                    ExecutionResult evalR = stepUnitExecutor.executeSingleStep(evaluatorStep, context, threadId, userId, tokens, xid, memoryCtx);
                    results.add(evalR);

                    //每次判断检查点前都把IterationState保存到上下文中
                    if (evalR.getCommand() != null) {
                        saveIterationState(context, loop.getLoopId(), iteration, "EVALUATE", 0);
                    }
                    if (!graphCommonDataProcessor.postProcessStepResult(evalR, evaluatorStep, context, plan, results.size() - 1, xid, userId, threadId)) {
                        //循环内部有中断步骤，就中断直接返回
                        return results;
                    }
                }

                int score = extractQualityScore(evaluatorStep, context);
                if (score >= loop.getQualityThreshold()) {
                    log.info("循环 [{}] 达标，score={}, threshold={}", loop.getLoopId(), score, loop.getQualityThreshold());
                    break;
                }
                log.info("循环 [{}] 未达标，score={}, threshold={}", loop.getLoopId(), score, loop.getQualityThreshold());

                // 进入纠正阶段
                if (correctorStep != null) {
                    phase = "CORRECT";
                } else {
                    // 没有纠正步骤，直接下一轮评估（但通常不建议）
                    iteration++;
                    phase = "EVALUATE";
                    continue;
                }
            }

            // 执行纠正步骤
            if ("CORRECT".equals(phase)) {
                // 清除上一次纠正的输出，确保每次循环都重新纠正
                if (correctorStep != null) {
                    context.remove(correctorStep.getId() + ".output");
                }
                if (!graphCommonDataProcessor.isStepCompleted(correctorStep, context)) {
                    // 标记为纠正步骤
                    correctorStep.setIterativeCorrectionStepFlag(true);

                    ExecutionResult correctR = stepUnitExecutor.executeSingleStep(correctorStep, context, threadId, userId, tokens, xid, memoryCtx);
                    results.add(correctR);
                    //每次判断检查点前都把IterationState保存到上下文中
                    if (correctR.getCommand() != null) {
                        saveIterationState(context, loop.getLoopId(), iteration, "CORRECT", 0);
                    }
                    if (!graphCommonDataProcessor.postProcessStepResult(correctR, correctorStep, context, plan, results.size() - 1, xid, userId, threadId)) {
                        //循环内部有中断步骤，就中断直接返回
                        return results;
                    }

                    // 关键：用纠正步骤的输出覆盖主步骤的输出键
                    context.put(loop.getFirstStepId() + ".output", correctR.getOutput());
                }

                iteration++;
                phase = "EVALUATE"; // 下一轮从评估开始
            }

            //每次循环结束后，也把状态保存到上下文中
            saveIterationState(context, loop.getLoopId(), iteration, phase, 0);
        }

        return results;
    }

    /**
     * 加载循环执行的相位记忆点
     */
    private IterationState loadIterationState(Map<String, Object> context, String loopId) {
        Object stateObj = context.get("iterationState_" + loopId);
        if (stateObj == null) return null;
        try {
            Map<String, Object> state = objectMapper.readValue(stateObj.toString(), Map.class);
            IterationState is = new IterationState();
            is.setIteration(((Number) state.get("iteration")).intValue());
            is.setPhase((String) state.get("phase"));
            is.setMainStepIndex(((Number) state.getOrDefault("mainStepIndex", 0)).intValue());
            return is;
        } catch (Exception e) {
            log.error("加载循环状态失败", e);
            return null;
        }
    }

    /**
     * 记录每个循环内部的循环状态，只有内层执行循环时需要用到。
     * 可以这样理解：外层是“按图索骥”，靠步骤完成标记就能恢复；内层是“原地绕圈”，必须自己记住绕到第几圈、当前朝向哪儿。
     **/
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
                try {
                    String finalContent = TextFormatTools.removeMDHeadTailAnnotation((String) content);
                    Map<String, Object> scoreMap = objectMapper.readValue(finalContent, Map.class);
                    if (scoreMap.containsKey("score")) {
                        return ((Number) scoreMap.get("score")).intValue();
                    }
                } catch (Exception e) {
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