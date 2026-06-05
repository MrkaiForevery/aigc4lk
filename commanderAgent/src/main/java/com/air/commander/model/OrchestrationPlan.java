package com.air.commander.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 编排计划实体
 */
@Data
@Builder
public class OrchestrationPlan {
    private String planId;
    private ExecutionMode executionMode;
    private List<Step> steps;
    private CorrectionConfig correctionConfig;
    private CompetitiveConfig competitiveConfig;
    private OnReject onReject;

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
        private String action;
    }
}