package com.air.commander.intent;

import com.air.commander.entity.IntentAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class IntentClassifier {

    private final ChatClient intentClient;
    private final ObjectMapper objectMapper;

    public IntentClassifier(
            @Qualifier("intentClassificationClient") ChatClient intentClient) {
        this.intentClient = intentClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 分析用户意图
     */
    public IntentAnalysis analyzeIntent(String userInput) {
        log.debug("Analyzing intent for input: {}", truncate(userInput, 200));
        
        try {
            //调用大模型进行意图分析
            String response = intentClient.prompt()
                    .user(userMessage -> userMessage.text(
                            "请分析以下用户输入的意图：\n" + userInput
                    ))
                    .call()
                    .content();

            // 清理响应，提取JSON
            String jsonStr = extractJson(response);
            Map<String, Object> result = objectMapper.readValue(jsonStr, Map.class);
            
            IntentAnalysis analysis = IntentAnalysis.builder()
                .scenario(getString(result, "scenario", "GENERAL"))
                .complexity(getString(result, "complexity", "MEDIUM"))
                .requiredCapabilities(getList(result, "required_capabilities", List.of("CHAT")))
                .modality(getString(result, "modality", "TEXT"))
                .confidence(getDouble(result, "confidence", 0.5))
                .build();
            
            log.info("Intent analysis result: scenario={}, complexity={}, modality={}, confidence={}",
                analysis.getScenario(), analysis.getComplexity(), 
                analysis.getModality(), analysis.getConfidence());
            
            return analysis;
            
        } catch (Exception e) {
            log.error("Intent analysis failed, using defaults", e);
            // 返回默认意图
            return IntentAnalysis.builder()
                .scenario("GENERAL")
                .complexity("MEDIUM")
                .requiredCapabilities(List.of("CHAT"))
                .modality("TEXT")
                .confidence(0.3)
                .build();
        }
    }

    /**
     * 从LLM响应中提取JSON
     */
    private String extractJson(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }
        
        // 尝试提取```json ... ```块
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        
        return "{}";
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> map, String key, List<String> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return defaultValue;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}