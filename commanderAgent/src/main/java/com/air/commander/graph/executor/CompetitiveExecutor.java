package com.air.commander.graph.executor;

import com.air.commander.graph.GraphBuilder;
import com.air.commander.graph.common.GraphCommonDataProcessor;
import com.air.commander.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 竞争模式执行器
 */
@Slf4j
@Component
public class CompetitiveExecutor {

    private final StepUnitExecutor stepUnitExecutor;
    private final SequentialExecutor sequentialExecutor;
    private final GraphCommonDataProcessor graphCommonDataProcessor;
    private final GraphBuilder graphBuilder;
    // 固定线程池，替代虚拟线程
    private final ExecutorService parallelExecutor;

    public CompetitiveExecutor(StepUnitExecutor stepUnitExecutor,
                               SequentialExecutor sequentialExecutor,
                               GraphCommonDataProcessor graphCommonDataProcessor,
                               GraphBuilder graphBuilder) {
        this.stepUnitExecutor = stepUnitExecutor;
        this.sequentialExecutor = sequentialExecutor;
        this.graphCommonDataProcessor = graphCommonDataProcessor;
        this.graphBuilder = graphBuilder;
        this.parallelExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }


    /**
     * 竞争模式执行（支持多竞争组、每组独立评审、组内多步骤竞争者）
     */
    public List<ExecutionResult> executeCompetitive(OrchestrationPlan plan,
                                                    String threadId, String userId,
                                                    Map<String, String> tokens, String xid,
                                                    MemoryContext memoryCtx,
                                                    Map<String, Object> runtimeContext) {
        if (memoryCtx != null && memoryCtx.getUserQuery() != null) {
            runtimeContext.put("userQuery", memoryCtx.getUserQuery());
        }

        OrchestrationPlan.CompetitiveConfig config = plan.getCompetitiveConfig();
        if (config == null || config.getGroups() == null || config.getGroups().isEmpty()) {
            log.warn("COMPETITIVE 模式缺少有效的 competitiveConfig，降级为顺序执行");
            return sequentialExecutor.executeSequential(plan, threadId, userId, tokens, xid, memoryCtx, runtimeContext);
        }

        // 拓扑排序后的完整步骤列表
        List<Step> orderedSteps = graphBuilder.buildSequentialExecutionOrder(plan);
        Map<String, Step> stepMap = orderedSteps.stream().collect(Collectors.toMap(Step::getId, Function.identity()));

        List<ExecutionResult> allResults = new ArrayList<>();
        Map<String, Object> context = new ConcurrentHashMap<>(runtimeContext);

        // 竞争状态恢复
        CompetitiveState state = loadCompetitiveState(context);
        Set<String> completedCompetitors = state != null ?
                new HashSet<>(state.getCompletedCompetitors()) : new HashSet<>();
        int currentGroupIndex = state != null ? state.getGroupIndex() : 0;

        int i = 0;
        while (i < orderedSteps.size()) {
            Step step = orderedSteps.get(i);

            // 检查该步骤是否属于某个竞争组（且该组尚未完成）
            OrchestrationPlan.CompetitiveGroup belongingGroup = findGroupForStep(config, step.getId(), currentGroupIndex);
            if (belongingGroup != null && !completedCompetitors.contains(belongingGroup.getGroupId())) {
                // 该步骤属于一个未完成的竞争组，触发该组的并行执行
                log.info("触发竞争组执行: groupId={}", belongingGroup.getGroupId());

                // 找到该组内所有的步骤ID，用于后续跳过
                Set<String> groupStepIds = belongingGroup.getCompetitors().stream()
                        .flatMap(c -> c.getStepIds().stream())
                        .collect(Collectors.toSet());

                // 新增：把评审步骤 ID 也加进去
                if (belongingGroup.getSelectorStepId() != null) {
                    groupStepIds.add(belongingGroup.getSelectorStepId());
                }

                // 并行执行所有竞争者
                Map<String, CompletableFuture<List<ExecutionResult>>> futures = new LinkedHashMap<>();
                for (OrchestrationPlan.Competitor competitor : belongingGroup.getCompetitors()) {
                    final String cid = competitor.getCompetitorId();
                    if (state != null && state.getCompletedCompetitors().contains(belongingGroup.getGroupId() + ":" + cid)) {
                        continue; // 恢复时跳过已完成的竞争者
                    }

                    CompletableFuture<List<ExecutionResult>> future = CompletableFuture.supplyAsync(() -> {
                        List<ExecutionResult> competitorResults = new ArrayList<>();
                        for (String stepId : competitor.getStepIds()) {
                            Step competitorStep = stepMap.get(stepId);
                            if (competitorStep == null) continue;
                            if (graphCommonDataProcessor.isStepCompleted(competitorStep, context)) continue;

                            ExecutionResult r = stepUnitExecutor.executeSingleStep(competitorStep, context, threadId, userId, tokens, xid, memoryCtx);
                            competitorResults.add(r);

                            // 检查中断
                            if (r.getCommand() != null) {
                                // 保存状态：记录当前竞争组和已完成的竞争者
                                saveCompetitiveState(context, currentGroupIndex, belongingGroup.getGroupId(),
                                        cid, getCompletedCompetitors(context, belongingGroup.getGroupId()));
                                return competitorResults;
                            }

                            // 必选步骤失败
                            if (!r.isSuccess() && competitorStep.isMandatory()) {
                                return competitorResults;
                            }
                        }
                        return competitorResults;
                    }, parallelExecutor);
                    futures.put(cid, future);
                }

                // 收集该组所有竞争者的结果
                Map<String, Object> competitorOutputs = new LinkedHashMap<>();
                boolean groupInterrupted = false;
                for (Map.Entry<String, CompletableFuture<List<ExecutionResult>>> entry : futures.entrySet()) {
                    String cid = entry.getKey();
                    try {
                        List<ExecutionResult> competitorResults = entry.getValue().join();
                        allResults.addAll(competitorResults);

                        // 检查是否有中断
                        for (ExecutionResult r : competitorResults) {
                            if (r.getCommand() != null) {
                                groupInterrupted = true;
                                break;
                            }
                        }
                        if (groupInterrupted) {
                            saveCompetitiveState(context, currentGroupIndex, belongingGroup.getGroupId(),
                                    cid, getCompletedCompetitors(context, belongingGroup.getGroupId()));
                            return allResults;
                        }

                        // 提取该竞争者的最终输出
                        ExecutionResult lastSuccess = competitorResults.stream()
                                .filter(ExecutionResult::isSuccess)
                                .reduce((a, b) -> b)
                                .orElse(null);
                        if (lastSuccess != null) {
                            competitorOutputs.put("competitor_" + cid, Map.of(
                                    "competitorId", cid,
                                    "output", lastSuccess.getOutput()
                            ));
                        }
                    } catch (Exception e) {
                        log.error("竞争者执行异常: competitorId={}", cid, e);
                    }
                }

                // 聚合输出
                context.put(belongingGroup.getGroupId() + ".output", competitorOutputs);
                completedCompetitors.add(belongingGroup.getGroupId()); // 标记该组已完成

                // 执行该组的评审步骤
                if (belongingGroup.getSelectorStepId() != null) {
                    Step selectorStep = stepMap.get(belongingGroup.getSelectorStepId());
                    if (selectorStep != null && !graphCommonDataProcessor.isStepCompleted(selectorStep, context)) {
                        //给竞争组的评审节点打上标识，用于后续执行通用的LLM_CALL时生成定制的提示词
                        selectorStep.setCompetitiveSelectorStepFlag(true);
                        ExecutionResult judgeResult = stepUnitExecutor.executeSingleStep(selectorStep, context, threadId, userId, tokens, xid, memoryCtx);
                        allResults.add(judgeResult);
                        if (!graphCommonDataProcessor.postProcessStepResult(judgeResult, selectorStep, context, plan,
                                allResults.size() - 1, xid, userId, threadId)) {
                            // 评审步骤被中断，保存竞争状态
                            saveCompetitiveState(context, currentGroupIndex, belongingGroup.getGroupId(),
                                    "selector", getCompletedCompetitors(context, belongingGroup.getGroupId()));
                            return allResults;
                        }
                    }
                }

                // 跳过该组内所有步骤，继续从组后第一个不在组内的步骤开始
                while (i < orderedSteps.size() && groupStepIds.contains(orderedSteps.get(i).getId())) {
                    i++;
                }
                continue;
            }

            // 普通步骤：不在任何竞争组内，或者竞争组已完成
            if (graphCommonDataProcessor.isStepCompleted(step, context)) {
                i++;
                continue;
            }

            ExecutionResult r = stepUnitExecutor.executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx);
            allResults.add(r);
            if (!graphCommonDataProcessor.postProcessStepResult(r, step, context, plan, allResults.size() - 1, xid, userId, threadId)) {
                return allResults;
            }
            i++;
        }


        return allResults;
    }


    private void saveCompetitiveState(Map<String, Object> context, int groupIndex, String groupId,
                                      String interruptedCompetitorId, List<String> completedCompetitors) {
        CompetitiveState state = CompetitiveState.builder()
                .groupIndex(groupIndex)
                .currentGroupId(groupId)
                .interruptedCompetitorId(interruptedCompetitorId)
                .completedCompetitors(completedCompetitors)
                .build();
        context.put("competitiveState", state);
    }

    private CompetitiveState loadCompetitiveState(Map<String, Object> context) {
        Object obj = context.get("competitiveState");
        if (obj instanceof CompetitiveState) return (CompetitiveState) obj;
        return null;
    }

    // 辅助方法：查找步骤所属的竞争组（仅在未完成的组中查找）
    private OrchestrationPlan.CompetitiveGroup findGroupForStep(OrchestrationPlan.CompetitiveConfig config, String stepId, int startIndex) {
        for (int idx = startIndex; idx < config.getGroups().size(); idx++) {
            OrchestrationPlan.CompetitiveGroup group = config.getGroups().get(idx);
            for (OrchestrationPlan.Competitor competitor : group.getCompetitors()) {
                if (competitor.getStepIds().contains(stepId)) {
                    return group;
                }
            }
        }
        return null;
    }

    private List<String> getCompletedCompetitors(Map<String, Object> context, String groupId) {
        String prefix = groupId + ".competitor_";
        return context.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(prefix.length()))
                .collect(Collectors.toList());
    }
}
