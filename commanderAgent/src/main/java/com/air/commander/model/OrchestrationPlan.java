package com.air.commander.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrchestrationPlan {
    //编排计划的唯一标识，用于日志追踪、案例入库、崩溃恢复时重新加载计划。
    private String planId;

    //定义整个计划的执行策略（顺序、并行、条件分支等）。GraphExecutor 根据它选择不同的执行器。
    private ExecutionMode executionMode;

    //任务的原子步骤列表。每个步骤包含类型（A2A/LLM/中断）、依赖关系、输入输出等。这是编排的最小执行单元。
    private List<Step> steps;

    //循环纠正模式的参数：最大迭代次数、质量阈值、评估者/纠正者Agent。当 executionMode = ITERATIVE_CORRECTION 时必填。
    private CorrectionConfig correctionConfig;

    //竞争执行模式的参数：多个Agent竞争执行同一任务，选择最优结果。当 executionMode = COMPETITIVE 时使用。
    private CompetitiveConfig competitiveConfig;

    //当用户拒绝授权/确认时的默认行为：回滚(rollback)、跳过(skip)或再次询问(ask_user)。
    private OnReject onReject;

    //是否启用自动回滚，以及回滚起始步骤（savepoint）。在必选步骤失败或用户拒绝时，GraphExecutor 会读取此配置决定是否撤销已完成操作。
    private RollbackConfig rollback;   // 新增：回滚配置

    public enum ExecutionMode {
        SEQUENTIAL, PARALLEL, CONDITIONAL, ITERATIVE_CORRECTION, COMPETITIVE, PIPELINE
    }

    @Data
    @Builder
    public static class CorrectionConfig {
        private int maxIterations;
        private int qualityThreshold;
        private String evaluatorAgent;
        private String correctorAgent;
    }

    @Data
    @Builder
    public static class CompetitiveConfig {
        private List<String> competitors;
        private String selectionCriteria;
    }

    @Data
    @Builder
    public static class OnReject {
        private String action;  // rollback / skip / ask_user
    }

    // 新增：回滚配置
    @Data
    @Builder
    public static class RollbackConfig {
        private boolean enabled;      // 是否启用回滚
        private String savepoint;     // 回滚起始步骤 ID（为空则从第一步开始回滚）
    }
}