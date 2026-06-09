package com.air.commander.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 候选计划评估结果（阶段3输出）
 */
@Data
@Builder
public class PlanEvaluationResult {
    private String winner;                              // 胜出候选 (A/B/C)
    private Map<String, DimensionScores> scores;        // 各候选的各维度评分
    private String reason;                              // 选择理由

    @Data
    @Builder
    public static class DimensionScores {
        private int agentAccuracy;              // Agent选择正确性 (1-10)
        private int dataFlow;         // 步骤逻辑合理性 (1-10)
        private int checkpoint;  // 检查点恰当性 (1-10)
        private int efficiency;                 // 执行效率 (1-10)
    }
}