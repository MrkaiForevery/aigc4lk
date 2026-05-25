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
public class StageResult {
    private String executionId;
    private String stage;        // INTENT_ANALYSIS, ARCHITECTURE_SELECTION, MODEL_SELECTION, EXECUTION
    private String status;       // progress, completed, error
    private String message;      // 人类可读的进度消息
    private Map<String, Object> data;  // 阶段结果数据
    
    public static StageResult progress(String executionId, String stage, String message) {
        return StageResult.builder()
                .executionId(executionId)
                .stage(stage)
                .status("progress")
                .message(message)
                .build();
    }
    
    public static StageResult completed(String executionId, String stage, Map<String, Object> data) {
        return StageResult.builder()
                .executionId(executionId)
                .stage(stage)
                .status("completed")
                .data(data)
                .build();
    }
    
    public static StageResult step(String executionId, String stage, Map<String, Object> stepData) {
        return StageResult.builder()
                .executionId(executionId)
                .stage(stage)
                .status("step")
                .data(stepData)
                .build();
    }
}