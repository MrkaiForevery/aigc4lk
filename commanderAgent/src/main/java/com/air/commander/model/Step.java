package com.air.commander.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 执行每一步的载体实体
 */
@Data
@Builder
public class Step {
    private String id;
    private StepType type;
    private String agent;
    private String task;
    private String model;
    private Map<String, Object> input;
    private List<String> dependsOn;
    private boolean mandatory;
    private long timeoutMs;
    private int retry;

    // ========== INTERRUPT 专用字段 ==========
    private String question;                    // 向用户展示的问题
    private List<String> options;               // 可选项（如 ["同意", "拒绝"]）

    // ========== Checkpoint 配置（新增） ==========
    private CheckpointConfig checkpoint;        // 检查点配置，非 null 时表示这是一个检查点

    // ========== 回滚相关（新增） ==========
    private String rollbackSavepoint;           // 回滚保存点标识（用于模板中指定回滚起始位置）

    /**
     * 检查点配置
     */
    @Data
    @Builder
    public static class CheckpointConfig {
        private CheckpointType type;            // 检查点类型
        private String question;                // 向用户展示的问题（可覆盖外层 question）
        private List<String> requiredScopes;    // 需要的权限范围（CREDENTIAL 类型时必填）
        private int timeoutMinutes;             // 超时时间（分钟），超过自动回滚
        private String onAgree;                 // 用户同意后跳转的步骤 ID
        private String onReject;                // 用户拒绝后执行的动作（rollback / skip）

        public enum CheckpointType {
            CREDENTIAL,  // 授权检查点：需要用户授予权限
            CONFIRM      // 确认检查点：需要用户确认中间结果
        }
    }

    public enum StepType {
        A2A_DELEGATE,
        INTERRUPT,
        LLM_CALL
    }
}