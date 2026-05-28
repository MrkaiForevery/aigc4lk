package com.air.memory.cleaner;

import com.air.memory.entity.CleanedMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 记忆清洗协调器
 * 协调规则清洗和 LLM 清洗的流程：
 * 1. 先执行规则清洗（低成本，同步）
 * 2. 如果规则清洗有效，再异步执行 LLM 清洗（高质量）
 * 3. 如果规则清洗无效，直接丢弃
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCleanerOrchestrator {

    private final RuleBasedCleaner ruleBasedCleaner;
    private final LLMBasedCleaner llmBasedCleaner;

    /**
     * 同步清洗（只做规则清洗，适合实时场景）
     */
    public CleanedMemory cleanSync(String rawContent, String memoryType) {
        return ruleBasedCleaner.clean(rawContent, memoryType);
    }

    /**
     * 异步清洗（规则清洗 + LLM 清洗，适合异步场景）
     */
    public CompletableFuture<CleanedMemory> cleanAsync(String rawContent, String memoryType) {
        // 第一步：规则清洗（同步，极快）
        CleanedMemory ruleCleaned = ruleBasedCleaner.clean(rawContent, memoryType);

        if (!ruleCleaned.isValid()) {
            log.debug("规则清洗后无效，丢弃: {}", rawContent);
            return CompletableFuture.completedFuture(ruleCleaned);
        }

        // 第二步：LLM 清洗（异步，较慢）
        return llmBasedCleaner.clean(ruleCleaned)
                .thenApply(llmCleaned -> {
                    log.info("LLM 清洗完成: valid={}, tags={}", 
                            llmCleaned.isValid(), llmCleaned.getProfileTags());
                    return llmCleaned;
                });
    }

    /**
     * 批量异步清洗
     */
    public CompletableFuture<java.util.List<CleanedMemory>> batchCleanAsync(
            java.util.List<String> rawContents, String memoryType) {
        java.util.List<CompletableFuture<CleanedMemory>> futures = rawContents.stream()
                .map(content -> cleanAsync(content, memoryType))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(CleanedMemory::isValid)
                        .toList());
    }
}