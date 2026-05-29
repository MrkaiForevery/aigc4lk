package com.air.memory.lifecycle;

import com.air.memory.config.LifecycleConfig;
import com.air.memory.service.BehaviorService;
import com.air.memory.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
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

    private final BehaviorService behaviorService;
    private final KnowledgeService knowledgeService;
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
            int deleted = behaviorService.deleteOlderThanAsync(threshold).get();
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
            int archived = behaviorService.archiveToHistoryAsync(threshold).get();
            if (archived > 0) {
                // 归档成功后删除原表数据
                behaviorService.deleteOlderThanAsync(threshold);
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
            int degraded = knowledgeService.degradeLowFrequency(3);
            log.info("降级完成，处理 {} 条低频知识", degraded);
        } catch (Exception e) {
            log.error("降级低频知识失败", e);
        }
    }

    /**
     * 手动触发清理（异步）
     */
    public void cleanupManually(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        behaviorService.deleteOlderThanAsync(threshold);
    }
}