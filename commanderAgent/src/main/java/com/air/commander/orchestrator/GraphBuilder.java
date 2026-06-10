package com.air.commander.orchestrator;

import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程图构建器
 * 执行流程图构建
 */
@Component
public class GraphBuilder {

    public List<Step> buildSequentialExecutionOrder(OrchestrationPlan plan) {
        return topologicalSequentialSort(plan.getSteps());
    }

    public List<List<Step>> buildParallelExecutionGroups(List<Step> steps){
        return topologicalParallelGroups(steps);
    }

    /**
     * 将步骤进行拓扑排序，输出一维度线性列表--->仅适用于Sequential顺序执行模式
     */
    private List<Step> topologicalSequentialSort(List<Step> steps) {
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


    /**
     * 将步骤进行拓扑排序，输出二维维度分层列表-->仅适用于Parallel带有并行的执行模式
     */
    private List<List<Step>> topologicalParallelGroups(List<Step> steps) {
        // 构建映射
        Map<String, Step> stepMap = steps.stream().collect(Collectors.toMap(Step::getId, Function.identity()));
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for (Step step : steps) {
            inDegree.putIfAbsent(step.getId(), 0);
            if (step.getDependsOn() != null) {
                for (String dep : step.getDependsOn()) {
                    inDegree.merge(step.getId(), 1, Integer::sum);
                    dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(step.getId());
                }
            }
        }

        List<List<Step>> groups = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        // 第一批入度为0的节点
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            List<Step> currentGroup = new ArrayList<>(queue.size());
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                String id = queue.poll();
                currentGroup.add(stepMap.get(id));
                // 更新依赖者的入度
                for (String dependent : dependents.getOrDefault(id, Collections.emptyList())) {
                    int newDegree = inDegree.merge(dependent, -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.offer(dependent);
                    }
                }
            }
            groups.add(currentGroup);
        }
        return groups;
    }
}