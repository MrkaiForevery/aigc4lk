package com.air.memory.service;

import com.air.memory.entity.DecisionRecord;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionService {

    private final StructuredMemoryRepository structuredMemoryRepository;

    public List<Map<String, Object>> getDecisionAnalysis() {
        return structuredMemoryRepository.getDecisionAnalysis();
    }

    @Async("ioExecutor")
    public CompletableFuture<List<Map<String, Object>>> getDecisionAnalysisAsync() {
        return CompletableFuture.completedFuture(this.getDecisionAnalysis());
    }

    @Transactional
    public void recordDecision(DecisionRecord record) {
        structuredMemoryRepository.recordDecision(record);
    }

    @Async("lightExecutor")
    @Transactional
    public CompletableFuture<Void> recordDecisionAsync(DecisionRecord record) {
        this.recordDecision(record);
        return CompletableFuture.completedFuture(null);
    }
}
