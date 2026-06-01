package com.air.commander.intent;

import com.air.api.dto.PreferenceMemoryDTO;
import com.air.api.dto.ProfileMemoryDTO;
import com.air.api.feignClient.MemoryPreferenceFeign;
import com.air.api.feignClient.MemoryProfileFeign;
import com.air.api.feignClient.MemorySessionFeign;
import com.air.commander.entity.IntentAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentClassifier {

    private final ChatClient intentClient;
    private final ObjectMapper objectMapper =  new ObjectMapper();

    private final MemorySessionFeign memorySessionFeign;
    private final MemoryProfileFeign memoryProfileFeign;
    private final MemoryPreferenceFeign memoryPreferenceFeign;

    /**
     * 分析用户意图
     */
    public IntentAnalysis analyzeIntent(String userInput,String sessionId, String userId) {
        log.debug("Analyzing intent for input: {}", truncate(userInput, 200));
        // 1. 加载会话记忆（第一层，必定加载）
        List<String> sessionHistory = new ArrayList<>();
        Optional.ofNullable(sessionId)
                .map(id -> memorySessionFeign.summarizeSession(id, userId))
                .ifPresentOrElse(sessionHistory::add,
                        () ->log.warn("Failed to load sessionHistory for sessionId={}",sessionId)
                );

        // 2. 加载用户画像（第二层，按需加载）
        ProfileMemoryDTO profile = null;
        PreferenceMemoryDTO preference = null;
        if (userId != null) {
            try {
                profile = memoryProfileFeign.getProfile(userId);
                preference = memoryPreferenceFeign.getPreference(userId);
            } catch (Exception e) {
                log.warn("Failed to load profile/preference for userId={}", userId);
            }
        }

        // 3. 构建增强的 Prompt
        String enhancedPrompt = buildEnhancedPrompt(userInput, sessionHistory, profile, preference);
        
        try {
            //调用大模型进行意图分析
            String response = intentClient.prompt()
                    .user(enhancedPrompt)
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

    private String buildEnhancedPrompt(String userInput,
                                       List<String> sessionHistory,
                                       ProfileMemoryDTO profile,
                                       PreferenceMemoryDTO preference) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下用户输入的意图：\n\n");

        // 会话上下文（如果有）
        if (sessionHistory != null && !sessionHistory.isEmpty()) {
            sb.append("## 历史对话上下文\n");
            for (String msg : sessionHistory) {
                sb.append(msg).append("\n");
            }
            sb.append("\n");
        }

        // 用户画像（如果有）
        if (profile != null) {
            sb.append("## 用户画像\n");
            sb.append("- 技术等级：").append(profile.getTechnicalLevel()).append("\n");
            sb.append("- 兴趣领域：").append(profile.getTopicsOfInterest()).append("\n");
            sb.append("- 沟通风格：").append(profile.getCommunicationStyle()).append("\n\n");
        }

        // 用户偏好（如果有）
        if (preference != null) {
            sb.append("## 用户偏好\n");
            sb.append("- 输出风格：").append(preference.getOutputStyle()).append("\n\n");
        }

        // 当前输入
        sb.append("## 当前用户输入\n");
        sb.append(userInput).append("\n\n");

        sb.append("请以JSON格式返回结果。");
        return sb.toString();
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