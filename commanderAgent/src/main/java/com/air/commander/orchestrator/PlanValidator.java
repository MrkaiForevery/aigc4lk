package com.air.commander.orchestrator;

import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.model.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 执行效果的的计划验证器
 */
@Component
@RequiredArgsConstructor
public class PlanValidator {

    private final BaseNacosA2ARouter baseNacosA2ARouter;

    public ValidationResult validate(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();
        if (hasCycle(plan.getSteps())) errors.add("Cycle detected");
        Set<String> agents = baseNacosA2ARouter.getAvailableAgents();
        for (Step step : plan.getSteps()) {
            if (step.getType() == Step.StepType.A2A_DELEGATE &&
                    step.getAgent() != null && !agents.contains(step.getAgent())) {
                errors.add("Agent not found: " + step.getAgent());
            }
        }
        return errors.isEmpty() ? new ValidationResult(true, List.of()) :
                new ValidationResult(false, errors);
    }

    private boolean hasCycle(List<Step> steps) {
        Map<String, List<String>> graph = new HashMap<>();
        for (Step s : steps) {
            graph.put(s.getId(), s.getDependsOn() != null ? s.getDependsOn() : List.of());
        }
        // DFS
        Set<String> visited = new HashSet<>(), recStack = new HashSet<>();
        for (String node : graph.keySet()) {
            if (dfs(node, graph, visited, recStack)) return true;
        }
        return false;
    }

    private boolean dfs(String node, Map<String, List<String>> graph, Set<String> visited, Set<String> recStack) {
        if (recStack.contains(node)) return true;
        if (visited.contains(node)) return false;
        visited.add(node);
        recStack.add(node);
        for (String n : graph.getOrDefault(node, List.of())) {
            if (dfs(n, graph, visited, recStack)) return true;
        }
        recStack.remove(node);
        return false;
    }
}

