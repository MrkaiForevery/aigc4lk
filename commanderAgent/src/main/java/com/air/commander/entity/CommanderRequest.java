package com.air.commander.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommanderRequest {
    private String userInput;
    private String sessionId;
    private Map<String, Object> context;
    private List<String> requiredCapabilities;
    
    // 多模态支持
    private String imageBase64;
    private String audioBase64;
    private String videoUrl;
    
    // 偏好设置
    private String preferredArchitecture;
    private String preferredModel;
    
    // 输出偏好
    private boolean streaming = false;
    private String outputFormat = "json"; // json, text, markdown
    
    // 获取输入模态
    public String getModality() {
        if (imageBase64 != null && !imageBase64.isEmpty()) return "IMAGE";
        if (audioBase64 != null && !audioBase64.isEmpty()) return "AUDIO";
        if (videoUrl != null && !videoUrl.isEmpty()) return "VIDEO";
        return "TEXT";
    }
    
    public boolean isMultimodal() {
        return !"TEXT".equals(getModality());
    }
}