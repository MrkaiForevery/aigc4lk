package com.air.commander.graph.common;

import com.air.commander.contract.DataContractEngine;
import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.model.StepDataContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GraphCommonDataProcessor {

    private final DataContractEngine dataContractEngine;
    private final InterruptHandler interruptHandler;

    public GraphCommonDataProcessor(DataContractEngine dataContractEngine,
                                    InterruptHandler interruptHandler) {
        this.dataContractEngine = dataContractEngine;
        this.interruptHandler = interruptHandler;
    }


    /**
     * 单步执行后的统一后处理
     * 包含：输出注册、中断检查、失败策略处理
     *
     * @return true = 正常继续，false = 需要停止执行（中断或回滚）
     */
    public boolean postProcessStepResult(ExecutionResult r, Step step,
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

    // 辅助方法：根据stepId查找Step
    public Step findStepById(List<Step> steps, String stepId) {
        return steps.stream().filter(s -> s.getId().equals(stepId)).findFirst().orElse(null);
    }


    /**
     * 根据 stepId 在有序步骤列表中查找索引
     */
    public int findStepIndex(List<Step> steps, String stepId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getId().equals(stepId)) return i;
        }
        return -1;
    }



}
