package com.air.commander.orchestrator;

import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 流程图构建器
 * 执行流程图构建
 */
@Component
public class GraphBuilder {

    public List<Step> buildExecutionOrder(OrchestrationPlan plan) {
        return topologicalSort(plan.getSteps());
    }

    private List<Step> topologicalSort(List<Step> steps) {
        Map<String, Step> stepMap = new LinkedHashMap<>();
        steps.forEach(s -> stepMap.put(s.getId(), s));
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        steps.forEach(s -> {
            inDegree.putIfAbsent(s.getId(), 0);
            if (s.getDependsOn() != null) {
                for (String dep : s.getDependsOn()) {
                    inDegree.merge(s.getId(), 1, Integer::sum);
                    dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.getId());
                }
            }
        });
        Queue<String> queue = new LinkedList<>();
        steps.forEach(s -> { if (inDegree.get(s.getId()) == 0) queue.offer(s.getId()); });
        List<Step> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(stepMap.get(id));
            for (String dep : dependents.getOrDefault(id, List.of())) {
                int deg = inDegree.merge(dep, -1, Integer::sum);
                if (deg == 0) queue.offer(dep);
            }
        }
        return sorted;
    }
}