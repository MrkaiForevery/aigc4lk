package com.air.commander.model;

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
public class ExecutionResult {
    private String relationPlanId;
    private String stepId;
    private boolean success;
    private ExecutionStatus executionStatus;
    private Map<String, Object> output;
    private String error;
    private Command command;
    private long durationMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Command {
        private String type;
        private String scope;
        private String message;
        private List<String> requiredScopes;
    }

    public enum ExecutionStatus{
        SUSPEND,DONE,FAILURE,
    }
}