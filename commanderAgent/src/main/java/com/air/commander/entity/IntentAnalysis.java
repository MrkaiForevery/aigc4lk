package com.air.commander.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentAnalysis {
    /**场景**/
    private String scenario;

    /**复杂度**/
    private String complexity;

    /**所需能力**/
    private List<String> requiredCapabilities;

    /**模态**/
    private String modality;

    /**置信度**/
    private double confidence;
}