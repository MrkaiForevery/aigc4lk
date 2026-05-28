package com.air.memory.service;

import cn.hutool.json.JSONUtil;
import com.air.memory.cleaner.MemoryCleanerOrchestrator;
import com.air.memory.deduplicator.MemoryDeduplicator;
import com.air.memory.entity.BehaviorRecord;
import com.air.memory.entity.CleanedMemory;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorService {

    private final StructuredMemoryRepository structuredMemoryRepository;
    /**
     * 记忆清清洗工具类
     **/
    private final MemoryCleanerOrchestrator cleanerOrchestrator;
    /**
     *记忆去重工具类
     */
    private final MemoryDeduplicator memoryDeduplicator;

    public List<BehaviorRecord> getRecentBehavior(String userId, int limit) {
        return structuredMemoryRepository.getRecentBehavior(userId, limit);
    }

    @Async("ioExecutor")
    public CompletableFuture<List<BehaviorRecord>> getRecentBehaviorAsync(String userId, int limit) {
        return CompletableFuture.completedFuture(this.getRecentBehavior(userId, limit));
    }

    @Transactional
    public void recordBehavior(BehaviorRecord record) {
        structuredMemoryRepository.recordBehavior(record);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> recordBehaviorAsync(BehaviorRecord record) {
        this.recordBehavior(record);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 带有清洗后的的异步写入
     */
    @Async("ioExecutor")
    @Transactional
    public CompletableFuture<Void> recordBehaviorWithCleanAsync(String userId, String sessionId,
                                                                String actionType, String rawContent) {
        // 先清洗
        return cleanerOrchestrator.cleanAsync(rawContent, "BEHAVIOR")
                .thenAccept(cleaned -> {
                    if (cleaned.isValid()) {
                        // ✅ 使用 JSONUtil 将 Map 转为 JSON 字符串
                        String metadataJson = JSONUtil.toJsonStr(Map.of(
                                "raw_content", cleaned.getRawContent(),
                                "confidence", cleaned.getConfidence(),
                                "clean_source", cleaned.getCleanSource()
                        ));
                        BehaviorRecord record = BehaviorRecord.builder()
                                .userId(userId)
                                .sessionId(sessionId)
                                .actionType(actionType)
                                .content(cleaned.getSummary())  // 使用清洗后的摘要
                                .metadata(metadataJson)
                                .build();
                        this.recordBehavior(record);
                    }
                });
    }


    /**
     * 带有去重后的异步写回
     */
    @Async("ioExecutor")
    @Transactional
    public CompletableFuture<Void> recordBehaviorWithDedupAsync(String userId, String sessionId,
                                                                String actionType, String rawContent) {
        return cleanerOrchestrator.cleanAsync(rawContent, "BEHAVIOR")
                .thenCompose(cleaned -> {
                    if (!cleaned.isValid()) return CompletableFuture.completedFuture(null);
                    // 行为记忆也可以做语义去重，但阈值可能更低，避免丢失细节
                    return memoryDeduplicator.isDuplicateAsync(cleaned.getSummary(), 0.95)
                            .thenAccept(isDup -> {
                                if (isDup) {
                                    log.debug("行为记录重复，跳过写入: {}", cleaned.getSummary());
                                } else {
                                    BehaviorRecord record = buildBehaviorRecord(userId, sessionId, actionType, cleaned);
                                    this.recordBehavior(record);
                                }
                            });
                });
    }

    /**
     * 手动清理过期记忆（供管理接口调用）
     */
    @Transactional
    public int cleanupExpiredBehavior(int retentionDays) {
        return structuredMemoryRepository.cleanupExpiredBehavior(retentionDays);
    }

    /**
     * 异步手动清理
     */
    @Transactional
    @Async("ioExecutor")
    public CompletableFuture<Integer> cleanupExpiredBehaviorAsync(int retentionDays) {
        return CompletableFuture.completedFuture(this.cleanupExpiredBehavior(retentionDays));
    }


    private BehaviorRecord buildBehaviorRecord(String userId, String sessionId, String actionType, CleanedMemory cleaned) {
        String metadataJson = JSONUtil.toJsonStr(Map.of(
                "raw_content", cleaned.getRawContent(),
                "confidence", cleaned.getConfidence(),
                "clean_source", cleaned.getCleanSource()
        ));
        return BehaviorRecord.builder()
                .userId(userId)
                .sessionId(sessionId)
                .actionType(actionType)
                .content(cleaned.getSummary())
                .metadata(metadataJson)
                .build();
    }
}
