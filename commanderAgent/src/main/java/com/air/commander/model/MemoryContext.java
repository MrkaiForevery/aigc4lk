package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 记忆上下文实体
 */
@Data
@Builder
public class MemoryContext {
    private List<Message> recentMessages;
    private Map<String, Object> userProfile;
    private Map<String, String> preferences;
    private List<Map<String, Object>> similarCases;
    private List<String> knowledgeChunks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}