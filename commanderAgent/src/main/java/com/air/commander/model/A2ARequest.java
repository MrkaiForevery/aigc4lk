package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A2A请求包装实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class A2ARequest {
    private String threadId, xid, stepId, task;
    private Map<String, Object> input;
    private Metadata metadata;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Metadata {
        private List<Map<String, String>> recentMessages;
        private Map<String, Object> userProfile;
        private Map<String, String> credentialTokens;
    }
}