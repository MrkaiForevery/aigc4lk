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
    private final MemoryServiceClient memoryClient;
    private final CaseLibraryClient caseClient;
    private final ResilienceManager resilience;

    @Async
    public void update(String threadId, String userId,
                       OrchestrationPlan plan,
                       List<ExecutionResult> results,
                       int score, MemoryContext oldCtx) {
        // 更新会话
        // 1. 更新会话（L0 短期记忆）
        try {
            resilience.executeWithCBAndTimeout("redis-session", "memory-write",
                    () -> {
                        conversationManager.addMessage(threadId, "assistant", "done");
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

        // 2. 记录用户行为（L1 持久记忆）
        try {
            resilience.executeWithCBAndTimeout("memory-service", "memory-write",
                    () -> {
                        Map<String, Object> behaviorData = Map.of(
                                "planId", plan.getPlanId(),
                                "stepCount", plan.getSteps().size(),
                                "executionMode", plan.getExecutionMode().name(),
                                "timestamp", System.currentTimeMillis()
                        );
                        memoryClient.recordBehavior(userId, behaviorData);
                        return null;
                    },
                    () -> {
                        log.warn("记录用户行为降级: userId={}, planId={}", userId, plan.getPlanId());
                        return null;
                    }
            );
        } catch (Exception e) {
            log.error("记录用户行为失败: userId={}, planId={}", userId, plan.getPlanId(), e);
        }


        // 3. 高质量案例入库（L2 经验记忆）
        if (score >= 85) {
            try {
                // 提取关键信息，避免序列化整个庞大的 plan
                Map<String, Object> caseData = Map.of(
                        "planId", plan.getPlanId(),
                        "executionMode", plan.getExecutionMode().name(),
                        "steps", extractStepSummaries(plan.getSteps()), // 自定义方法，只提取步骤摘要
                        "score", score,
                        "userInput", oldCtx != null ? oldCtx.getUserQuery() : ""
                );
                resilience.executeWithCBAndTimeout("chroma-case", "case-write",
                        () -> {
                            caseClient.saveCase(caseData);
                            log.info("案例已入库: planId={}, score={}", plan.getPlanId(), score);
                            return null;
                        },
                        () -> {
                            log.warn("案例入库降级: planId={}", plan.getPlanId());
                            return null;
                        }
                );
            } catch (Exception e) {
                log.error("案例入库失败: planId={}", plan.getPlanId(), e);
            }
        }
    }

    /**
     * 提取步骤摘要，避免存储完整的 input/output 导致数据膨胀
     */
    private List<Map<String, Object>> extractStepSummaries(List<Step> steps) {
        return steps.stream()
                .map(step -> Map.of(
                        "id", step.getId(),
                        "type", step.getType().name(),
                        "task", step.getTask(),
                        "agent", step.getAgent() != null ? step.getAgent() : "",
                        "dependsOn", step.getDependsOn() != null ? step.getDependsOn() : List.of()
                ))
                .collect(Collectors.toList());
    }
}