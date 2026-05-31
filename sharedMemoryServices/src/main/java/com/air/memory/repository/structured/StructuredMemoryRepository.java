package com.air.memory.repository.structured;

import cn.hutool.core.bean.BeanUtil;
import com.air.api.dto.IdentityMemoryDTO;
import com.air.api.dto.PreferenceMemoryDTO;
import com.air.api.dto.ProfileMemoryDTO;
import com.air.memory.cleaner.MemoryCleanerOrchestrator;
import com.air.memory.deduplicator.MemoryDeduplicator;
import com.air.memory.entity.*;
import com.air.memory.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 结构化记忆数据访问层（异步优化版）
 * 写操作使用 lightExecutor 线程池，查询操作使用 ioExecutor 线程池，
 * 以支持上层并行调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredMemoryRepository {

    private final IdentityMemoryMapper identityMapper;
    private final ProfileMemoryMapper profileMapper;
    private final BehaviorRecordMapper behaviorMapper;
    private final PreferenceMemoryMapper preferenceMapper;
    private final RelationshipRecordMapper relationshipMapper;
    private final DecisionRecordMapper decisionMapper;

    /**
     * 记忆清清洗工具类
     **/
    private final MemoryCleanerOrchestrator cleanerOrchestrator;

    /**
     * 记忆去重工具类
     */
    private final MemoryDeduplicator memoryDeduplicator;


    // ==================== 身份记忆 ====================
    // 同步方法保留，方便不需要异步的场景
    public IdentityMemory getIdentity(String userId) {
        return identityMapper.selectById(userId);
    }

    public void saveIdentity(IdentityMemoryDTO identityMemoryDTO) {
        IdentityMemory identityMemory = IdentityMemory.builder().build();
        BeanUtil.copyProperties(identityMemoryDTO, identityMemory);
        identityMapper.insertOrUpdate(identityMemory);
    }

    public void deleteIdentity(String userId) {
        identityMapper.deleteById(userId);
    }

    // ==================== 画像记忆 ====================
    public ProfileMemoryDTO getProfile(String userId) {
        ProfileMemory profileMemory = profileMapper.selectById(userId);
        ProfileMemoryDTO profileMemoryDTO = new ProfileMemoryDTO();
        BeanUtil.copyProperties(profileMemory, profileMemoryDTO);
        return profileMemoryDTO;
    }

    public void updateProfile(ProfileMemoryDTO profile) {
        ProfileMemory profileMemory = new ProfileMemory();
        BeanUtil.copyProperties(profile, profileMemory);
        profileMapper.insertOrUpdate(profileMemory);
    }

    // ==================== 行为记忆 ====================
    public List<BehaviorRecord> getRecentBehavior(String userId, int limit) {
        return behaviorMapper.selectRecent(userId, limit);
    }

    public void recordBehavior(BehaviorRecord record) {
        behaviorMapper.insert(record);
    }

    public int deleteOlderThan(LocalDateTime threshold) {
        return behaviorMapper.deleteOlderThan(threshold);
    }

    public int archiveToHistory(LocalDateTime threshold) {
        return behaviorMapper.archiveToHistory(threshold);
    }

    /**
     * 手动清理过期记忆（供管理接口调用）
     */
    public int cleanupExpiredBehavior(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        return behaviorMapper.deleteOlderThan(threshold);
    }

    // ==================== 偏好记忆 ====================
    public PreferenceMemoryDTO getPreference(String userId) {
        PreferenceMemoryDTO preferenceMemoryDTO = new PreferenceMemoryDTO();
        PreferenceMemory preferenceMemory = preferenceMapper.selectById(userId);
        BeanUtil.copyProperties(preferenceMemory, preferenceMemoryDTO);
        return preferenceMemoryDTO;
    }

    public void updatePreference(PreferenceMemory preference) {
        preferenceMapper.insertOrUpdate(preference);
    }

    // ==================== 关系记忆 ====================

    public List<RelationshipRecord> getRelationships(String userId) {
        return relationshipMapper.findByUserId(userId);
    }

    public void addRelationship(RelationshipRecord record) {
        relationshipMapper.insert(record);
    }


    // ==================== 决策记忆 ====================
    public void recordDecision(DecisionRecord record) {
        decisionMapper.insert(record);
    }

    public List<Map<String, Object>> getDecisionAnalysis() {
        return decisionMapper.countByScenario();
    }
}