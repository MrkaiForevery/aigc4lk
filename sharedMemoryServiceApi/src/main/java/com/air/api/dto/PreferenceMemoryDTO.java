package com.air.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// 偏好记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceMemoryDTO implements Serializable {
    private String userId;
    private String outputStyle;
    private Boolean showIntermediate;
    private String preferredModels;   // JSON
    private Boolean autoFallback;
    private LocalDateTime updatedAt;
}