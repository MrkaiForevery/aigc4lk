package com.air.dataAnalysis.entity;


import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiDimensionResult {
    public Map<String, Map<String, Double>> data;
    public List<String> dimensions;
    public List<String> metrics;
    public String aggregation;
}