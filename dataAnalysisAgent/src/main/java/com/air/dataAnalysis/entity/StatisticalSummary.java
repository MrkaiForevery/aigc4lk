package com.air.dataAnalysis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticalSummary {
    public double mean;
    public double variance;
    public double stdDev;
    public double min;
    public double max;
    public long count;
}