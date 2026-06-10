package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 步骤的数据契约，定义输入输出规范
 */
@Data
@Builder
@NoArgsConstructor   // ← 添加
@AllArgsConstructor  // ← 添加
public class StepDataContract {
    
    // 输入契约：声明需要从全局上下文中获取哪些数据
    private List<InputField> inputFields;
    
    // 输出契约：声明本步骤会产生什么数据
    private OutputField outputField;
    
    // 失败策略
    private FailurePolicy onFailure;
    
    // 最大输入大小（字符数），默认 5000
    private int maxInputSize;
    
    @Data
    @Builder
    @NoArgsConstructor   // ← 添加
    @AllArgsConstructor  // ← 添加
    public static class InputField {
        private String name;           // 在全局上下文中的 key（如 "step1.output"）
        private String alias;          // 传递给步骤时的别名（如 "carData"）
        private boolean required;      // 是否必填
        private int maxLength;         // 最大长度，超过则截断
        private String defaultValue;   // 默认值（当上下文不存在时使用）
    }
    
    @Data
    @Builder
    @NoArgsConstructor   // ← 添加
    @AllArgsConstructor  // ← 添加
    public static class OutputField {
        private String name;           // 输出在全局上下文中的 key（如 "step2.output"）
        private String description;    // 输出描述
    }
    
    public enum FailurePolicy {
        ROLLBACK_AND_STOP,    // 回滚并停止
        SKIP_AND_CONTINUE,    // 跳过并继续
        MARK_AS_FAILED        // 标记为失败，让后续步骤决定
    }
}