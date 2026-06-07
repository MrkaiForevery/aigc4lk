package com.air.commander.memory;

import com.air.commander.conversation.ConversationManager;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.resilience.ResilienceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 内存更新流水线
 */
@Component
@RequiredArgsConstructor
public class MemoryUpdatePipeline {

    private final ConversationManager conversationManager;
    private final MemoryServiceClient memoryClient;
    private final CaseLibraryClient caseClient;
    private final ResilienceManager resilience;

    @Async
    public void update(String threadId, String userId,
                       OrchestrationPlan plan,
                       List<ExecutionResult> results,
                       int score, MemoryContext oldCtx) {
        // 更新会话
        resilience.executeWithCBAndTimeout("redis-session",
                "memory-write",
                () -> {
                    conversationManager.addMessage(threadId, "assistant", "done");
                    return null;
                },
                () -> null
        );
        // 记录行为
        resilience.executeWithCBAndTimeout("memory-service", "memory-write",
                () -> {
                    memoryClient.recordBehavior(userId, Map.of("plan", plan.getPlanId()));
                    return null;
                },
                () -> null);
        // 案例入库
        if (score >= 85) {
            resilience.executeWithCBAndTimeout("chroma-case", "case-write",
                    () -> {
                        caseClient.saveCase(Map.of("plan", plan, "score", score));
                        return null;
                    },
                    () -> null);
        }
    }
}