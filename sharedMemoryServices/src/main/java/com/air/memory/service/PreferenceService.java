package com.air.memory.service;

import com.air.api.dto.PreferenceMemoryDTO;
import com.air.memory.entity.PreferenceMemory;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final StructuredMemoryRepository structuredMemoryRepository;

    public PreferenceMemoryDTO getPreference(String userId) {
        return structuredMemoryRepository.getPreference(userId);
    }

    @Async("ioExecutor")
    public CompletableFuture<PreferenceMemoryDTO> getPreferenceAsync(String userId) {
        return CompletableFuture.completedFuture(this.getPreference(userId));
    }

    @Transactional
    public void updatePreference(PreferenceMemory preference) {
        structuredMemoryRepository.updatePreference(preference);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> updatePreferenceAsync(PreferenceMemory preference) {
        this.updatePreference(preference);
        return CompletableFuture.completedFuture(null);
    }
}
