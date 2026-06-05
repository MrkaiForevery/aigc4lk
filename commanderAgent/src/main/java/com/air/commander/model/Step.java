package com.air.commander.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 步骤实体
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
    private String question;
    private List<String> options;

    public enum StepType {
        A2A_DELEGATE, INTERRUPT, LLM_CALL
    }
}