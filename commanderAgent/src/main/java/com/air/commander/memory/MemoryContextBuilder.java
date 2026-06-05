package com.air.commander.memory;

import com.air.commander.conversation.ConversationManager;
import com.air.commander.model.MemoryContext;
import com.air.commander.resilience.ResilienceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 记忆的上下文，todo 看后面MemoryContext要不要使用knowledgeChunks这个记忆
 */
@Component
@RequiredArgsConstructor
public class MemoryContextBuilder {

    private final ConversationManager conversationManager;
    private final MemoryServiceClient memoryServiceClient;
    private final CaseLibraryClient caseLibraryClient;
    private final ResilienceManager resilience;

    public MemoryContext build(String userId, String threadId, String userInput) {
        List<Map<String, String>> recentMessages = (List<Map<String, String>>) resilience.executeWithCBAndTimeout(
                "redis-session", "memory-query",
                () -> conversationManager.getRecentMessages(threadId, 20),
                () -> List.of()
        );
        Map<String, Object> profile = resilience.executeWithCBAndTimeout(
                "memory-service", "memory-query",
                () -> memoryServiceClient.getProfile(userId),
                () -> Map.of("industry", "general")
        );
        Map<String, String> preferences = resilience.executeWithCBAndTimeout(
                "memory-service", "memory-query",
                () -> memoryServiceClient.getPreferences(userId),
                () -> Map.of()
        );
        List<Map<String, Object>> similarCases = resilience.executeWithCBAndTimeout(
                "chroma-case", "case-retrieve",
                () -> caseLibraryClient.searchSimilar(userInput, 3),
                () -> List.of()
        );

        return MemoryContext.builder()
                .recentMessages(recentMessages)
                .userProfile(profile)
                .preferences(preferences)
                .similarCases(similarCases)
                .build();
    }
}