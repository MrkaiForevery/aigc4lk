package com.air.memory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 决策记忆记录实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_decision")
public class DecisionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String sessionId;
    private String intentScenario;
    private String selectedArchitecture;
    private String selectedModel;
    private String selectionReason;
    private Long executionTimeMs;
    private Boolean success;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}