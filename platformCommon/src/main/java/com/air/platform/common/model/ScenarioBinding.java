package com.air.platform.common.model;

import com.air.platform.common.enums.ModalityType;
import lombok.Builder;
import lombok.Data;

/**
 * 处理场景绑定承接实体
 */
@Data
@Builder
public class ScenarioBinding {
    private String scenario;
    private String primaryArchitecture;
    private String defaultModel;
    private String fallbackModel;
    private String modelOverride;  // 可选，覆盖默认模型
    private ModalityType defaultModality;
}

