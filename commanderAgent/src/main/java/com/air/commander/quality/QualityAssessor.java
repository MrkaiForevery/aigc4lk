package com.air.commander.quality;

import com.air.commander.model.ExecutionResult;
import com.air.commander.model.OrchestrationPlan;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 质量评估器
 */
@Component
public class QualityAssessor {

    public int evaluate(OrchestrationPlan plan, List<ExecutionResult> results) {
        // 简单评分：成功步骤比例 * 100
        long success = results.stream().filter(ExecutionResult::isSuccess).count();
        return (int) (success * 100 / Math.max(1, results.size()));
    }
}