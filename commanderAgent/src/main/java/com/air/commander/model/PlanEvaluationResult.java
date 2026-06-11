package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 竞争模式生成的候选计划评估结果实体（评估阶段输出）
 */
@Data
@Builder
@NoArgsConstructor   // ← 添加
@AllArgsConstructor  // ← 添加
public class PlanEvaluationResult {
    private String winner;                              // 胜出候选 (A/B/C)
    private Map<String, DimensionScores> scores;        // 各候选的各维度评分
    private String reason;                              // 选择理由

    @Data
    @Builder
    @NoArgsConstructor   // ← 添加
    @AllArgsConstructor  // ← 添加
    public static class DimensionScores {
        private int agentAccuracy;              // Agent选择正确性 (1-10)
        private int dataFlow;         // 步骤逻辑合理性 (1-10)
        private int executionMode;    // 执行模式选择合理性
        private int checkpoint;  // 检查点恰当性 (1-10)
        private int efficiency;                 // 执行效率 (1-10)
    }
}