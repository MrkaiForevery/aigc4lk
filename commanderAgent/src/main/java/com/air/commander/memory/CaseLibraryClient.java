package com.air.commander.memory;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/**
 * 案例搜索client模块，todo 先模拟 后面看看是不是单独创建服务，暴露给其调用
 */
@Component
public class CaseLibraryClient {
    public List<Map<String, Object>> searchSimilar(String userInput, int topK) {
        return List.of(); // 模拟空
    }
    public void saveCase(Map<String, Object> caseData) {
        // 模拟保存
    }
}