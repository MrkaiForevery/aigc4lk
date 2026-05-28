package com.air.memory.service;

import com.air.api.dto.IdentityMemoryDTO;
import com.air.memory.entity.IdentityMemory;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class IdentityMemoryService {

    private final StructuredMemoryRepository structuredMemoryRepository;


    public IdentityMemory getIdentity(String userId) {
       return structuredMemoryRepository.getIdentity(userId);
    }

    @Async("ioExecutor")
    public CompletableFuture<IdentityMemory> getIdentityAsync(String userId) {
        return CompletableFuture.completedFuture(structuredMemoryRepository.getIdentity(userId));
    }

    @Transactional
    public void saveIdentity(IdentityMemoryDTO entity) {
        structuredMemoryRepository.saveIdentity(entity);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> saveIdentityAsync(IdentityMemoryDTO identityMemoryDTO) {
        this.saveIdentity(identityMemoryDTO);
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public void deleteIdentity(String userId) {
        structuredMemoryRepository.deleteIdentity(userId);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> deleteIdentityAsync(String userId) {
        this.deleteIdentity(userId);
        return CompletableFuture.completedFuture(null);
    }

}
