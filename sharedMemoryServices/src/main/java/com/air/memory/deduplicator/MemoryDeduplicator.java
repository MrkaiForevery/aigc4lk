package com.air.memory.deduplicator;

import com.air.memory.config.DedupEnableConfig;
import com.air.memory.repository.vectorized.KnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 记忆去重引擎
 * 支持基于向量相似度的去重，以及简单的规则去重（精确匹配）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDeduplicator {

    private final KnowledgeRepository knowledgeRepository;
    private final DedupEnableConfig dedupEnableConfig;

    /** 默认相似度阈值，超过此值视为重复 */
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.92;

    /**
     * 同步检查是否重复（适用于实时场景）
     * @param content 待检查的内容
     * @return true 表示重复
     */
    public boolean isDuplicate(String content) {
        return isDuplicate(content, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public boolean isDuplicate(String content, double threshold) {
        if (content == null || content.isBlank()) return false;
        return knowledgeRepository.existsSimilar(content, threshold);
    }

    /**
     * 异步检查是否重复（适用于异步流程）
     */
    @Async("ioExecutor")
    public CompletableFuture<Boolean> isDuplicateAsync(String content) {
        return CompletableFuture.completedFuture(isDuplicate(content));
    }

    @Async("ioExecutor")
    public CompletableFuture<Boolean> isDuplicateAsync(String content, double threshold) {
        return CompletableFuture.completedFuture(isDuplicate(content, threshold));
    }

    /**
     * 简单规则去重：精确内容匹配（可选，适用于非向量化场景）
     * 此处仅为示例，实际可结合数据库精确查询实现
     */
    public boolean isExactMatch(String content, String existingContent) {
        if (content == null || existingContent == null) return false;
        // 简单比较：去除首尾空格后完全相等
        return content.trim().equals(existingContent.trim());
    }
}