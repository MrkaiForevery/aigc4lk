package com.air.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

//用户行为记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorRecordDTO implements Serializable {
    private Long id;
    private String userId;
    private String sessionId;
    private String actionType;
    private String content;
    private String metadata;
    private LocalDateTime createdAt;
}