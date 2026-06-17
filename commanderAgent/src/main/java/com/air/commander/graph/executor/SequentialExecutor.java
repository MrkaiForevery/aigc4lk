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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SequentialExecutor {

    private final GraphBuilder graphBuilder;
    private final StepUnitExecutor stepUnitExecutor;
    private final GraphCommonDataProcessor graphCommonDataProcessor;

    /**
     * 单步骤执行
     **/
    public SequentialExecutor(GraphBuilder graphBuilder,
                              StepUnitExecutor stepUnitExecutor,
                              GraphCommonDataProcessor graphCommonDataProcessor) {
        this.graphBuilder = graphBuilder;
        this.stepUnitExecutor = stepUnitExecutor;
        this.graphCommonDataProcessor = graphCommonDataProcessor;
    }


    public List<ExecutionResult> executeSequential(OrchestrationPlan plan,
                                                   String threadId,
                                                   String userId,
                                                   Map<String, String> tokens,
                                                   String xid,
                                                   MemoryContext memoryCtx,
                                                   Map<String, Object> runtimeContext) {

        log.info("以Sequential模式开始执行任务..... ");
        long start = System.currentTimeMillis();

        List<Step> orderedSteps = graphBuilder.buildSequentialExecutionOrder(plan);

        // 注入 userQuery 到全局上下文
        if (memoryCtx != null && memoryCtx.getUserQuery() != null) {
            runtimeContext.put("userQuery", memoryCtx.getUserQuery());
        }

        List<ExecutionResult> results = new ArrayList<>();

        for (int i = 0; i < orderedSteps.size(); i++) {
            Step step = orderedSteps.get(i);

            // 跳过已完成的步骤（恢复时使用）
            if (runtimeContext.containsKey(step.getId() + ".output") ||
                    runtimeContext.containsKey(step.getId() + ".interrupted")) {
                continue;
            }

            ExecutionResult r = stepUnitExecutor.executeSingleStep(step, runtimeContext, threadId, userId, tokens, xid, memoryCtx);
            results.add(r);

            if (!graphCommonDataProcessor.postProcessStepResult(r, step, runtimeContext, plan, i, xid, userId, threadId)) {
                break; // 中断或回滚，停止执行
            }
        }
        long end = System.currentTimeMillis();
        log.info("以Sequential模式执行任务结束，本次耗时:{}ms ", end - start);
        return results;
    }


}
