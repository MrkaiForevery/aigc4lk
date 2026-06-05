package com.air.commander.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 执行结果实体
 */
@Data
@Builder
public class ExecutionResult {
    private String stepId;
    private boolean success;
    private Map<String, Object> output;
    private String error;
    private Command command;
    private long durationMs;

    @Data @Builder
    public static class Command {
        private String type;
        private String scope;
        private String message;
        private List<String> requiredScopes;
    }
}