package com.air.memory.service;

import com.air.memory.cleaner.MemoryCleanerOrchestrator;
import com.air.memory.entity.ProfileMemory;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final StructuredMemoryRepository structuredMemoryRepository;

    /**
     * 记忆清清洗工具类
     **/
    private final MemoryCleanerOrchestrator cleanerOrchestrator;

    public ProfileMemory getProfile(String userId) {
        return structuredMemoryRepository.getProfile(userId);
    }

    @Async("ioExecutor")
    public CompletableFuture<ProfileMemory> getProfileAsync(String userId) {
        return CompletableFuture.completedFuture(this.getProfile(userId));
    }

    public void updateProfile(ProfileMemory profile) {
        structuredMemoryRepository.updateProfile(profile);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> updateProfileAsync(ProfileMemory profile) {
        this.updateProfile(profile);
        return CompletableFuture.completedFuture(null);
    }


    /**
     * 带记忆清洗的数据异步写
     */
    @Async("ioExecutor")
    @Transactional
    public CompletableFuture<Void> updateProfileWithCleanAsync(String userId, String rawContent) {
        return cleanerOrchestrator.cleanAsync(rawContent, "PROFILE")
                .thenAccept(cleaned -> {
                    if (cleaned.isValid() && cleaned.getProfileTags() != null
                            && !cleaned.getProfileTags().isEmpty()) {
                        ProfileMemory profile = getProfile(userId);
                        if (profile == null) {
                            profile = ProfileMemory.builder()
                                    .userId(userId)
                                    .topicsOfInterest("[]")
                                    .build();
                        }
                        // 合并标签
                        profile.setTopicsOfInterest(mergeTags(profile.getTopicsOfInterest(), cleaned.getProfileTags()));
                        this.updateProfile(profile);
                        ;
                    }
                });
    }

    private String mergeTags(String existingTagsJson, List<String> newTags) {
        // todo 实现 JSON 数组合并逻辑
        // ...
        return existingTagsJson;
    }
}
