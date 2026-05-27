package com.air.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// 身份记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMemoryDTO implements Serializable {
    private String userId;
    private String username;
    private String phone;
    private String email;
    private String memberLevel;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}








