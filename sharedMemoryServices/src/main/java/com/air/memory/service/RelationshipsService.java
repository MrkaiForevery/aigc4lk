package com.air.memory.service;

import com.air.memory.entity.RelationshipRecord;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipsService {

    private final StructuredMemoryRepository structuredMemoryRepository;

    public List<RelationshipRecord> getRelationships(String userId) {
        return structuredMemoryRepository.getRelationships(userId);
    }

    @Async("ioExecutor")
    public CompletableFuture<List<RelationshipRecord>> getRelationshipsAsync(String userId) {
        return CompletableFuture.completedFuture(this.getRelationships(userId));
    }

    @Transactional
    public void addRelationship(RelationshipRecord record) {
        structuredMemoryRepository.addRelationship(record);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> addRelationshipAsync(RelationshipRecord record) {
        this.addRelationship(record);
        return CompletableFuture.completedFuture(null);
    }

}
