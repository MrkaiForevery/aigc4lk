package com.air.memory.repository.structured;

import cn.hutool.core.bean.BeanUtil;
import com.air.api.dto.IdentityMemoryDTO;
import com.air.memory.entity.*;
import com.air.memory.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 结构化记忆数据访问层（异步优化版）
 * 写操作使用 lightExecutor 线程池，查询操作使用 ioExecutor 线程池，
 * 以支持上层并行调用。
 */
@Component
@RequiredArgsConstructor
public class StructuredMemoryRepository {

    private final IdentityMemoryMapper identityMapper;
    private final ProfileMemoryMapper profileMapper;
    private final BehaviorRecordMapper behaviorMapper;
    private final PreferenceMemoryMapper preferenceMapper;
    private final RelationshipRecordMapper relationshipMapper;
    private final DecisionRecordMapper decisionMapper;

    // ==================== 身份记忆 ====================

    @Async("ioExecutor")
    public CompletableFuture<IdentityMemory> getIdentityAsync(String userId) {
        return CompletableFuture.completedFuture(identityMapper.selectById(userId));
    }

    // 同步方法保留，方便不需要异步的场景
    public IdentityMemory getIdentity(String userId) {
        return identityMapper.selectById(userId);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> saveIdentityAsync(IdentityMemoryDTO identityMemoryDTO) {
        IdentityMemory identityMemory = IdentityMemory.builder().build();
        BeanUtil.copyProperties(identityMemoryDTO, identityMemory);
        identityMapper.insertOrUpdate(identityMemory);
        return CompletableFuture.completedFuture(null);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> deleteIdentityAsync(String userId) {
        identityMapper.deleteById(userId);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 画像记忆 ====================

    @Async("ioExecutor")
    public CompletableFuture<ProfileMemory> getProfileAsync(String userId) {
        return CompletableFuture.completedFuture(profileMapper.selectById(userId));
    }

    public ProfileMemory getProfile(String userId) {
        return profileMapper.selectById(userId);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> updateProfileAsync(ProfileMemory profile) {
        profileMapper.insertOrUpdate(profile);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 行为记忆 ====================

    @Async("ioExecutor")
    public CompletableFuture<List<BehaviorRecord>> getRecentBehaviorAsync(String userId, int limit) {
        return CompletableFuture.completedFuture(behaviorMapper.selectRecent(userId, limit));
    }

    public List<BehaviorRecord> getRecentBehavior(String userId, int limit) {
        return behaviorMapper.selectRecent(userId, limit);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> recordBehaviorAsync(BehaviorRecord record) {
        behaviorMapper.insert(record);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 偏好记忆 ====================

    @Async("ioExecutor")
    public CompletableFuture<PreferenceMemory> getPreferenceAsync(String userId) {
        return CompletableFuture.completedFuture(preferenceMapper.selectById(userId));
    }

    public PreferenceMemory getPreference(String userId) {
        return preferenceMapper.selectById(userId);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> updatePreferenceAsync(PreferenceMemory preference) {
        preferenceMapper.insertOrUpdate(preference);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 关系记忆 ====================

    @Async("ioExecutor")
    public CompletableFuture<List<RelationshipRecord>> getRelationshipsAsync(String userId) {
        return CompletableFuture.completedFuture(relationshipMapper.findByUserId(userId));
    }

    public List<RelationshipRecord> getRelationships(String userId) {
        return relationshipMapper.findByUserId(userId);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> addRelationshipAsync(RelationshipRecord record) {
        relationshipMapper.insert(record);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 决策记忆 ====================

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> recordDecisionAsync(DecisionRecord record) {
        decisionMapper.insert(record);
        return CompletableFuture.completedFuture(null);
    }

    @Async("ioExecutor")
    public CompletableFuture<List<Map<String, Object>>> getDecisionAnalysisAsync() {
        return CompletableFuture.completedFuture(decisionMapper.countByScenario());
    }

    public List<Map<String, Object>> getDecisionAnalysis() {
        return decisionMapper.countByScenario();
    }
}