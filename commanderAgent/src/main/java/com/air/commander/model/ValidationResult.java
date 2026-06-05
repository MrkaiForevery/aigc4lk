package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 校验结果实体
 */
@Data
@Builder
@AllArgsConstructor
public class ValidationResult {
    private boolean valid;
    private List<String> errors;
}