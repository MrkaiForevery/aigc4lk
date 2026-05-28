package com.air.memory.lifecycle;

import com.air.memory.config.LifecycleConfig;
import com.air.memory.mapper.BehaviorRecordMapper;
import com.air.memory.repository.vectorized.KnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 记忆生命周期管理器
 * 负责过期数据清理、冷热分离、低频知识降级
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryLifecycleManager {

    private final BehaviorRecordMapper behaviorMapper;
    private final KnowledgeRepository knowledgeRepository;
    private final RedissonClient redissonClient;

    private final LifecycleConfig lifecycleConfig;

    /**
     * 每天凌晨3点执行：清理90天前的行为记忆
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredBehavior() {
        log.info("开始清理过期行为记忆...");
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(90);
            int deleted = behaviorMapper.deleteOlderThan(threshold);
            log.info("清理完成，删除 {} 条过期行为记录", deleted);
        } catch (Exception e) {
            log.error("清理过期行为记忆失败", e);
        }
    }

    /**
     * 每周日凌晨4点执行：归档3个月前的冷数据
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    public void archiveColdBehaviorData() {
        log.info("开始归档冷行为数据...");
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMonths(3);
            int archived = behaviorMapper.archiveToHistory(threshold);
            if (archived > 0) {
                // 归档成功后删除原表数据
                behaviorMapper.deleteOlderThan(threshold);
            }
            log.info("归档完成，处理 {} 条记录", archived);
        } catch (Exception e) {
            log.error("归档冷行为数据失败", e);
        }
    }

    /**
     * 每天凌晨2点执行：降级低频知识记忆
     * 将3个月内未被访问过的知识从 Chroma 主集合移到归档集合
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void degradeLowFrequencyKnowledge() {
        log.info("开始降级低频知识记忆...");
        try {
            // 这里需要根据你的 Chroma 使用情况实现具体降级逻辑
            // 例如：查询 metadata 中 last_access_time 早于 3 个月前的文档
            // 将其从主集合移动到 archive 集合
            int degraded = knowledgeRepository.degradeLowFrequency(3);
            log.info("降级完成，处理 {} 条低频知识", degraded);
        } catch (Exception e) {
            log.error("降级低频知识失败", e);
        }
    }

    /**
     * 手动触发清理（异步）
     */
    @Async("ioExecutor")
    public void cleanupManually(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        behaviorMapper.deleteOlderThan(threshold);
    }
}