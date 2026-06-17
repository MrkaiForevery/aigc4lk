package com.air.api.dto.conversation;

import com.air.api.dto.enums.ModeType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 生成的执行计划实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrchestrationPlanDTO {

    //编排计划关联的唯一requestId
    private String relationRequestId;

    //严格场景下编排计划关联的templateId
    private String relationTemplateId;

    //编排计划的唯一标识，用于日志追踪、案例入库、崩溃恢复时重新加载计划。
    private String planId;

    //意图识别走的模式 是 "template" 或 "dynamic"
    private ModeType mode;

    //定义整个计划的执行策略（顺序、并行、条件分支等）。GraphExecutor 根据它选择不同的执行器。
    private ExecutionMode executionMode;

    //任务的原子步骤列表。每个步骤包含类型（A2A/LLM/中断）、依赖关系、输入输出等。这是编排的最小执行单元。
    private List<StepDTO> steps;

    //多循环校验纠正模式的参数。当 executionMode = ITERATIVE_CORRECTION 时必填。
    private CorrectionConfig correctionConfig;

    //竞争执行模式的参数：多个Agent竞争执行同一任务，选择最优结果。当 executionMode = COMPETITIVE 时使用。
    private CompetitiveConfig competitiveConfig;

    //当用户拒绝授权/确认时的默认行为：回滚(rollback)、跳过(skip)或再次询问(ask_user)。
    private OnReject onReject;

    //是否启用自动回滚，以及回滚起始步骤（savepoint）。在必选步骤失败或用户拒绝时，GraphExecutor 会读取此配置决定是否撤销已完成操作。
    private RollbackConfig rollback;   // 新增：回滚配置

    public enum ExecutionMode {
        SEQUENTIAL, PARALLEL, CONDITIONAL, ITERATIVE_CORRECTION, COMPETITIVE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorrectionConfig {
        // 支持多个循环
        private List<CorrectionLoop> loops;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorrectionLoop {
        private String loopId;              // 循环的唯一标识
        private String firstStepId;         // 起始步骤 ID--该步骤只执行一次，不在循环内重复执行
        private String evaluatorStepId;     // 评估步骤 ID（循环结束标志）
        private String correctorStepId;     // 修正步骤 ID（可选）
        private int maxIterations;          // 最大迭代次数
        private int qualityThreshold;       // 质量阈值
        private boolean checkpointAfterEachIteration; // 每轮循环后是否插入确认检查点
        private boolean checkpointOnMaxIterations;  // 达到最大迭代次数后是否插入检查点
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitiveConfig {
        private List<CompetitiveGroup> groups;        // 多个竞争组（顺序执行）
        private String selectionCriteria;             // 选择标准描述（如“选最全面的”、“选最准确的”）
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitiveGroup {
        private String groupId;                       // 竞争组唯一标识
        private List<Competitor> competitors;         // 竞争元素列表
        private String selectorStepId;                // 本组的评审步骤 ID（必须存在于全局 steps 中）
        private String selectionCriteria;             // 本组的选择标准（可选，覆盖全局配置）
        private int maxConcurrency;                   // 最大并行度（可选，默认等于竞争者数量）
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Competitor {
        private String competitorId;                  // 竞争者标识
        private List<String> stepIds;                 // 该竞争者包含的步骤 ID 列表（在全局 steps 中定义）
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnReject {
        private String action;  // rollback / skip / ask_user
    }

    // 新增：回滚配置
    @Data
    @Builder
    @NoArgsConstructor   // ← 添加
    @AllArgsConstructor  // ← 添加
    public static class RollbackConfig {
        private boolean enabled;      // 是否启用回滚
        private String savepoint;     // 回滚起始步骤 ID（为空则从第一步开始回滚）
    }
}