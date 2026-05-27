package com.air.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// 关系记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipRecordDTO implements Serializable {
    private Long id;
    private String userId;
    private String relatedUser;
    private String relationType;
    private String projectId;
    private LocalDateTime createdAt;
}