package com.air.commander.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 记忆上下文实体
 */
@Data
@Builder
public class MemoryContext {
    private List<Map<String, String>> recentMessages;
    private Map<String, Object> userProfile;
    private Map<String, String> preferences;
    private List<Map<String, Object>> similarCases;
    private List<String> knowledgeChunks;
}