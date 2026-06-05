package com.air.commander.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 执行计划实体
 */
@Data
@Builder
public class ExecutionPlan {
    private String mode;
    private String planId;
    private List<ExecutionResult> results;
    private boolean interrupted;
    private String summary;
    private String xid;
    private Map<String, Object> context;
}