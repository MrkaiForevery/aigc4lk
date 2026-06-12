package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 核心承载实体类
 * 可执行计划的每一步骤的载体实体
 */
@Data
@Builder
@NoArgsConstructor   // ← 添加
@AllArgsConstructor  // ← 添加
public class Step {

    private String id;

    /**步骤的执行类型**/
    private StepType type;

    /**当type = A2A_DELEGATE 时，指定要调用的远程 Agent 名称**/
    private String agent;

    /**任务的自然语言描述，告诉执行者“具体做什么**/
    private String task;

    /**指定要使用的模型名称（如 "fast-model"、"reasoning-model"）**/
    private String model;

    /**步骤执行时的输入参数。可以包含具体的值，也可以包含占位符 {stepX.output} 来引用前序步骤的输出**/
    private Map<String, Object> input;

    /**定义步骤的前置依赖，列表中的值必须是其他步骤的 id**/
    private List<String> dependsOn;

    /**定该步骤是否为强制步骤。如果必选步骤执行失败（且未触发检查点），GraphExecutor 会停止后续步骤并触发回滚（如果启用了回滚配置）**/
    private boolean mandatory;

    /**步骤的最大执行时间（毫秒）**/
    private long timeoutMs;

    /**步骤失败时的重试次数**/
    private int retry; //

    // ========== INTERRUPT 专用字段 ==========
    /**向用户展示的问题**/
    private String question;

    /**可选项（如 ["同意", "拒绝"]）**/
    private List<String> options;

    // ========== Checkpoint 配置（新增） ==========
    /** 检查点配置，非 null 时表示这是一个检查点**/
    private CheckpointConfig checkpoint;

    // ========== 回滚相关（新增） ==========
     /**回滚保存点标识（用于模板中指定回滚起始位置）**/
    private String rollbackSavepoint;

    // ========== 条件执行模式（新增） ==========
    /**条件执行模式的条件判断参数配置**/
    private ConditionConfig conditionConfig;

    /** 数据契约，定义输入输出规范**/
    private StepDataContract dataContract;

    /** 控制每一步LLM执行时是否需要读取用户历史的会话记忆,默认false不包含对话历史**/
    private boolean includeChatHistory = false;

    /**标记该步骤是否是竞争模式里面得评审步骤**/
    private boolean competitiveSelectorStepFlag = false;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionConfig {
        private String expression;           // SPEL 表达式或自然语言描述
        private String evaluationMethod;     // "SPEL" | "LLM_JUDGE"

        // 多路分支：key=条件值，value=跳转的步骤ID
        private Map<String, String> branches;  // 如 {"下滑": "step_fix", "平稳": "step_summary", "增长": "step_expand"}

        private String defaultStepId;         // 默认分支（如果所有条件都不匹配）
    }

    public enum StepType {
        A2A_DELEGATE,
        INTERRUPT,
        LLM_CALL
    }
}