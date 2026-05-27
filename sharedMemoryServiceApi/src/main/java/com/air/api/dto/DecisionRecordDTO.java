package com.air.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// 决策记忆记录实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRecordDTO implements Serializable {
    private Long id;
    private String userId;
    private String sessionId;
    private String intentScenario;
    private String selectedArchitecture;
    private String selectedModel;
    private String selectionReason;
    private Long executionTimeMs;
    private Boolean success;
    private LocalDateTime createdAt;
}