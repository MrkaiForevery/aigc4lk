package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 校验结果实体
 */
@Data
@Builder
@NoArgsConstructor   // ← 添加
@AllArgsConstructor  // ← 添加
public class ValidationResult {
    private boolean valid;
    private List<String> errors;
}