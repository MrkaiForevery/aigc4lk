package com.air.platform.core.architecture;

import com.air.platform.enums.ArchitectureType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Builder
public class ParallelArchitecture implements AgentArchitecture {
    
    @Getter
    private final String id;
    
    @Getter
    private final String name;
    
    @Getter
    private final ArchitectureType type = ArchitectureType.PARALLEL;
    
    private final List<Object> agents;
    private Object model;
    private Object toolProvider;
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public AgentArchitecture withModel(Object model) {
        this.model = model;
        return this;
    }
    
    @Override
    public AgentArchitecture withTools(Object toolProvider) {
        this.toolProvider = toolProvider;
        return this;
    }
    
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        log.info("Executing ParallelArchitecture: {} with {} agents", id, agents.size());
        
        List<CompletableFuture<Map<String, Object>>> futures = agents.stream()
            .map(agent -> CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    Map<String, Object> result = invokeAgent(agent, input);
                    log.debug("Agent executed in {}ms", 
                        System.currentTimeMillis() - startTime);
                    return result;
                } catch (Exception e) {
                    log.error("Agent execution failed", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("error", e.getMessage());
                    return errorResult;
                }
            }, executor))
            .collect(Collectors.toList());
        
        // 等待所有任务完成
        List<Map<String, Object>> results = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        // 合并结果
        Map<String, Object> mergedResult = new HashMap<>();
        mergedResult.put("parallel_results", results);
        
        // 如果有汇聚Agent，进行最终处理
        if (hasAggregator()) {
            mergedResult = aggregateResults(mergedResult, input);
        }
        
        return mergedResult;
    }
    
    @Override
    public Map<String, Object> executeStream(Map<String, Object> input) {
        return execute(input);
    }
    
    private boolean hasAggregator() {
        return false;  // 简化实现
    }
    
    private Map<String, Object> aggregateResults(Map<String, Object> results, 
                                                   Map<String, Object> input) {
        return results;
    }
    
    private Map<String, Object> invokeAgent(Object agent, Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        result.put("agent_result", "executed");
        return result;
    }
}