package com.air.commander.memory;

import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 记忆模块client，todo 先模拟，后面进行改造，使用feign从memory-services里面获取
 */
@Component
public class MemoryServiceClient {


    public Map<String, Object> getProfile(String userId) {
        return Map.of("industry", "finance", "role", "analyst");
    }

    public Map<String, String> getPreferences(String userId) {
        return Map.of("language", "zh-CN", "detail", "high");
    }

    public void recordBehavior(String userId, Map<String, Object> behavior) {
        // 模拟记录
    }
}