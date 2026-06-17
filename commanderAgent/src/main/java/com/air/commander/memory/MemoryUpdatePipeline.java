package com.air.commander.memory;

import com.air.commander.conversation.ConversationManager;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.resilience.ResilienceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内存更新流水线
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryUpdatePipeline {

    private final ConversationManager conversationManager;
    private final ResilienceManager resilience;

    @Async
    public void update(String threadId,
                       String userId,
                       String userInput,
                       OrchestrationPlan plan,
                       List<ExecutionResult> results,
                       MemoryContext oldCtx) {
        // 1. 更新会话（L0 短期记忆）
        try {
            resilience.executeWithCBAndTimeout(
                    "pg-conversation-history",
                    "conversation-history-write",
                    () -> {
                        try {
                            conversationManager.saveConversationHistory(threadId, userId, userInput, plan, results, oldCtx);
                        } catch (SQLException e) {
                            log.debug("会话消息添加失败: {}", e.getCause());
                            throw new RuntimeException(e);
                        }
                        log.debug("会话消息已添加: threadId={}", threadId);
                        return null;
                    },
                    () -> {
                        log.warn("添加会话消息降级: threadId={}", threadId);
                        return null;
                    }
            );
        } catch (Exception e) {
            log.error("更新会话失败: threadId={}", threadId, e);
        }
    }
}