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
    private String scenario;
    private String complexity;
    private List<String> requiredCapabilities;
    private String modality;
    private double confidence;
}