package com.air.platform.common.architecture;

import com.air.platform.common.enums.ArchitectureType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 序列化架构
 */
@Slf4j
@Builder
public class SequentialArchitecture implements AgentArchitecture {
    
    @Getter
    private final String id;
    
    @Getter
    private final String name;
    
    @Getter
    private final ArchitectureType type = ArchitectureType.SEQUENTIAL;
    
    private final List<Object> agents;
    private Object model;
    private Object toolProvider;
    
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
        log.info("Executing SequentialArchitecture: {}", id);
        
        Map<String, Object> context = new HashMap<>(input);
        List<Map<String, Object>> intermediateResults = new ArrayList<>();
        
        for (int i = 0; i < agents.size(); i++) {
            Object agent = agents.get(i);
            long startTime = System.currentTimeMillis();
            
            try {
                // 调用agent执行
                Map<String, Object> result = invokeAgent(agent, context);
                intermediateResults.add(result);
                
                // 将结果合并到上下文
                context.putAll(result);
                context.put("step_" + i + "_result", result);
                
                log.debug("Step {} completed in {}ms", i, 
                    System.currentTimeMillis() - startTime);
                
            } catch (Exception e) {
                log.error("Step {} failed", i, e);
                throw new RuntimeException("Sequential execution failed at step " + i, e);
            }
        }
        
        context.put("intermediate_results", intermediateResults);
        return context;
    }
    
    @Override
    public Map<String, Object> executeStream(Map<String, Object> input) {
        // 简化实现，实际应为流式处理
        return execute(input);
    }
    
    private Map<String, Object> invokeAgent(Object agent, Map<String, Object> context) {
        // 通过反射或直接调用agent的execute方法
        // 简化实现
        Map<String, Object> result = new HashMap<>();
        result.put("agent_result", "executed");
        return result;
    }
    
    public static class SequentialArchitectureBuilder {
        private List<Object> agents = new ArrayList<>();
        
        public SequentialArchitectureBuilder agents(List<Object> agents) {
            this.agents = agents;
            return this;
        }
        
        public SequentialArchitectureBuilder addAgent(Object agent) {
            this.agents.add(agent);
            return this;
        }
    }
}