package com.air.dataAnalysis.tools;

import com.air.dataAnalysis.entity.MultiDimensionResult;
import com.air.dataAnalysis.entity.StatisticalSummary;
import com.air.dataAnalysis.entity.TrendForecastResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

/**
 * todo 这个还可以扩展更多的数据分析计算方法
 */
@Component
public class DataAnalysisAbilityTools {

    /**
     * 模拟数据源（实际应连接数据仓库或API） todo 这里先只是模拟一下
     * 此处简化：接收JSON格式的数据数组
     */
    @Tool(description = "多维度分析：根据指定的维度和指标对数据进行聚合分析")
    public MultiDimensionResult multiDimensionAnalysis(
            @ToolParam(description = "数据集，格式为List<Map<String, Object>>，每个Map是一条记录") List<Map<String, Object>> dataset,
            @ToolParam(description = "维度列表，如['date', 'region']") List<String> dimensions,
            @ToolParam(description = "指标列表，如['sales', 'profit']") List<String> metrics,
            @ToolParam(description = "聚合函数，如'sum','avg','count'") String aggregation) {
        
        // 模拟聚合逻辑（实际应使用OLAP或Pandas-like计算）todo 完善实际逻辑
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (Map<String, Object> row : dataset) {
            String dimKey = dimensions.stream()
                    .map(d -> String.valueOf(row.get(d)))
                    .collect(Collectors.joining("|"));
            Map<String, Double> metricMap = result.getOrDefault(dimKey, new HashMap<>());
            for (String metric : metrics) {
                Number val = (Number) row.get(metric);
                double current = metricMap.getOrDefault(metric, 0.0);
                if ("sum".equalsIgnoreCase(aggregation)) {
                    metricMap.put(metric, current + val.doubleValue());
                } else if ("avg".equalsIgnoreCase(aggregation)) {
                    // 简化：暂不计次数，实际需要count
                    metricMap.put(metric, current + val.doubleValue());
                } else if ("count".equalsIgnoreCase(aggregation)) {
                    metricMap.put(metric, current + 1);
                }
            }
            result.put(dimKey, metricMap);
        }
        return new MultiDimensionResult(result, dimensions, metrics, aggregation);
    }

    @Tool(description = "趋势预测：基于时间序列数据预测未来N个周期")
    public TrendForecastResult trendForecast(
            @ToolParam(description = "时间序列数据，按时间升序排列的数值列表") List<Double> timeSeries,
            @ToolParam(description = "预测周期数") int periods,
            @ToolParam(description = "预测模型（simple_moving_average, exponential_smoothing）") String model) {
        
        // 简化：简单移动平均预测 todo 完善实际逻辑
        int window = 3;
        List<Double> predictions = new ArrayList<>();
        for (int i = 0; i < periods; i++) {
            double avg = 0;
            int start = Math.max(0, timeSeries.size() - window + i);
            for (int j = start; j < timeSeries.size(); j++) {
                avg += timeSeries.get(j);
            }
            avg = avg / Math.min(window, timeSeries.size() - start);
            predictions.add(avg);
            timeSeries.add(avg); // 递推
        }
        return new TrendForecastResult(predictions, "simple_moving_average", 0.8); // 置信度80%
    }

    @Tool(description = "统计摘要：计算数据集的基本统计量")
    public StatisticalSummary statisticalSummary(
            @ToolParam(description = "数值列表") List<Double> values) {
        
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / values.size();
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double min = values.stream().min(Double::compare).orElse(0.0);
        double max = values.stream().max(Double::compare).orElse(0.0);
        
        return new StatisticalSummary(mean, variance, stdDev, min, max, values.size());
    }

}