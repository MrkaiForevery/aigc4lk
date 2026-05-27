package com.air.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// ProfileMemory.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMemoryDTO implements Serializable {
    private String userId;
    private String preferredModel;
    private String preferredArchitecture;
    private String technicalLevel;
    private String topicsOfInterest;   // JSON
    private String communicationStyle;
    private String extraAttrs;         // JSON
    private LocalDateTime updatedAt;
}