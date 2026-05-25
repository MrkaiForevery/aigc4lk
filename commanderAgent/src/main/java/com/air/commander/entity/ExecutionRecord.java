package com.air.commander.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRecord {
    private String executionId;
    private String sessionId;
    private long timestamp;
    private String scenario;
    private String complexity;
    private String architectureId;
    private String modelId;
    private String modality;
    private long durationMs;
    private boolean success;
    private boolean fallback;
    private String errorMessage;
    private Map<String, Object> inputSummary;
    private Map<String, Object> outputSummary;
}