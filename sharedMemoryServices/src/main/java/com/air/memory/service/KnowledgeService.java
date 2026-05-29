package com.air.memory.service;

import com.air.memory.cleaner.MemoryCleanerOrchestrator;
import com.air.memory.deduplicator.MemoryDeduplicator;
import com.air.memory.entity.KnowledgeResult;
import com.air.memory.repository.vectorized.KnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 向量知识库操作服务
 * 注意：因为Chroma 不支持事务，所以这里不能加事务注解
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    /**
     * 数据仓库注入
     **/
    private final KnowledgeRepository knowledgeRepository;

    /**
     * 记忆清清洗工具类
     **/
    private final MemoryCleanerOrchestrator cleanerOrchestrator;

    /**
     * 记忆去重工具类
     */
    private final MemoryDeduplicator memoryDeduplicator;


    /**
     * 同步保存知识
     */
    public void save(String content, String userId, String source) {
        knowledgeRepository.save(content, userId, source);
    }

    /**
     * 异步保存知识
     */
    @Async("ioExecutor")
    public CompletableFuture<Void> saveAsync(String content, String userId, String source) {
        this.save(content, userId, source);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 批量异步保存
     */
    @Async("ioExecutor")
    public CompletableFuture<Void> batchSaveAsync(List<Map<String, String>> documents) {
        knowledgeRepository.batchSaveAsync(documents);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 只带清洗的数据异步写回
     */
    @Async("ioExecutor")
    public CompletableFuture<Void> saveWithCleanAsync(String rawContent, String userId, String source) {
        return cleanerOrchestrator.cleanAsync(rawContent, "KNOWLEDGE")
                .thenAccept(cleaned -> {
                    if (cleaned.isValid() && cleaned.getKnowledge() != null) {
                        knowledgeRepository.save(cleaned.getSummary(), userId, source);
                    }
                });
    }

    /**
     * 带清洗和去重的异步写回
     */
    @Async("ioExecutor")
    public CompletableFuture<Void> saveWithDedupAsync(String rawContent, String userId, String source) {
        return cleanerOrchestrator.cleanAsync(rawContent, "KNOWLEDGE")
                .thenCompose(cleaned -> {
                    if (!cleaned.isValid()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    // 去重检查
                    return memoryDeduplicator.isDuplicateAsync(cleaned.getSummary(), 0.90)
                            .thenAccept(isDup -> {
                                if (isDup) {
                                    log.debug("知识内容重复，跳过写入: {}", cleaned.getSummary());
                                } else {
                                    knowledgeRepository.save(cleaned.getSummary(), userId, source);
                                }
                            });
                });
    }

    /**
     * 同步搜索知识
     */
    public List<KnowledgeResult> search(String query, int limit) {
        return knowledgeRepository.search(query, limit);
    }

    /**
     * 异步搜索知识
     */
    @Async("ioExecutor")
    public CompletableFuture<List<KnowledgeResult>> searchAsync(String query, int limit) {
        List<KnowledgeResult> results = this.search(query, limit);
        return CompletableFuture.completedFuture(results);
    }


    public int degradeLowFrequency(int months) {
        return knowledgeRepository.degradeLowFrequency(months);
    }
}
