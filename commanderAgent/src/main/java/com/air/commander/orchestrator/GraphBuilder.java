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

    /**
     * 将步骤进行拓扑排序
     * why:
     * 步骤顺序不可信：LLM 可能把有依赖关系的步骤随意排列（例如把 step2 放在 step1 前面，但 step2 依赖 step1 的输出）。
     * 依赖关系存在于字段中：dependsOn 字段才是真正的执行顺序约束，而不是数组索引。
     * 支持并行识别：拓扑排序后，可以识别出哪些步骤没有依赖关系，从而支持并行执行。
     */
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