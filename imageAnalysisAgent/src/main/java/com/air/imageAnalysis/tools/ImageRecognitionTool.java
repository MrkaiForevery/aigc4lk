package com.air.imageAnalysis.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 目标检测工具（模拟，实际可调用云服务）todo
 */
@Component
public class ImageRecognitionTool {

    @Tool(description = "检测图像中的物体，返回检测到的物体列表及置信度")
    public List<Map<String, Object>> detectObjects(@ToolParam(description = "图像的 Base64 编码") String imageBase64) {
        // 模拟检测结果
        // 实际可调用阿里云视觉智能平台、百度AI等
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(Map.of("object", "person", "confidence", 0.95, "bbox", "[100,200,150,300]"));
        results.add(Map.of("object", "car", "confidence", 0.87, "bbox", "[400,500,600,550]"));
        return results;
    }
}