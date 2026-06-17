package com.air.memory.entity.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 执行结果实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResultEntity {
    private String relationPlanId;
    private String stepId;
    private boolean success;
    private Map<String, Object> output;
    private String error;
    private Command command;
    private long durationMs;

    @Data
    @Builder
    @NoArgsConstructor   // ← 添加
    @AllArgsConstructor  // ← 添加
    public static class Command {
        private String type;
        private String scope;
        private String message;
        private List<String> requiredScopes;
    }
}