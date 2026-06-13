package com.air.commander.orchestrator;

import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.model.ValidationResult;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 计划校验器
 * 对LLM生成的执行计划结果进行格式结构校验
 */
@Component
public class PlanValidator {

    private final BaseNacosA2ARouter baseNacosA2ARouter;

    public PlanValidator(BaseNacosA2ARouter baseNacosA2ARouter) {
        this.baseNacosA2ARouter = baseNacosA2ARouter;
    }

    public ValidationResult validateOrchestrationPlan(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();

        // L1 结构校验
        errors.addAll(validateStructure(plan));

        // L2 逻辑校验
        if (!OrchestrationPlan.ExecutionMode.COMPETITIVE.equals(plan.getExecutionMode())) {
            errors.addAll(validateLogic(plan));
        }


        // L3 业务语义（仅当 L1,L2 通过时可选择执行，但这里收集全部）
        errors.addAll(validateBusinessSemantics(plan));

        // L4 安全（可选）
        errors.addAll(validateSecurity(plan));

        // L5 定制化生成Competitive类型的plan时，校验依赖字段的合理性
        errors.addAll(validateCompetitiveDependStructure(plan));

        return new ValidationResult(errors.isEmpty(), errors);
    }

    // ==================== L1 结构完整性 ====================
    private List<String> validateStructure(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            errors.add("计划中没有步骤");
            return errors; // 后续校验无意义
        }
        for (int i = 0; i < plan.getSteps().size(); i++) {
            Step step = plan.getSteps().get(i);
            if (step.getId() == null || step.getId().isBlank()) {
                errors.add("步骤 " + i + " 缺少 id");
            }
            if (step.getType() == null) {
                errors.add("步骤 " + step.getId() + " 缺少 type");
            }
            // 检查 INTERRUPT 步骤的 checkpoint 结构
            if (step.getType() == Step.StepType.INTERRUPT && step.getCheckpoint() != null) {
                errors.addAll(validateCheckpointStructure(step));
            }
        }


        return errors;
    }

    private List<String> validateCheckpointStructure(Step step) {
        List<String> errors = new ArrayList<>();
        Step.CheckpointConfig cp = step.getCheckpoint();
        if (cp.getType() == null) {
            errors.add("步骤 " + step.getId() + " 的 checkpoint 缺少 type");
        }
        if (cp.getQuestion() == null || cp.getQuestion().isBlank()) {
            errors.add("步骤 " + step.getId() + " 的 checkpoint 缺少 question");
        }
        if (cp.getType() == Step.CheckpointConfig.CheckpointType.CREDENTIAL
                && (cp.getRequiredScopes() == null || cp.getRequiredScopes().isEmpty())) {
            errors.add("步骤 " + step.getId() + " 的 CREDENTIAL 检查点缺少 requiredScopes");
        }
        return errors;
    }

    private List<String> validateCompetitiveDependStructure(OrchestrationPlan plan){
        List<String> errors = new ArrayList<>();
        if (plan.getExecutionMode() == OrchestrationPlan.ExecutionMode.COMPETITIVE && plan.getCompetitiveConfig() != null) {
            for (OrchestrationPlan.CompetitiveGroup group : plan.getCompetitiveConfig().getGroups()) {
                Step selectorStep = findStepById(plan.getSteps(), group.getSelectorStepId());
                if (selectorStep != null) {
                    Set<String> requiredDeps = new HashSet<>();
                    // 收集所有竞争者的最后步骤 ID
                    for (OrchestrationPlan.Competitor competitor : group.getCompetitors()) {
                        if (!competitor.getStepIds().isEmpty()) {
                            String lastStepId = competitor.getStepIds().get(competitor.getStepIds().size() - 1);
                            requiredDeps.add(lastStepId);
                        }
                    }
                    // 检查评审步骤的 dependsOn 是否包含了所有必需的依赖
                    List<String> actualDeps = selectorStep.getDependsOn() != null ?
                            selectorStep.getDependsOn() : List.of();
                    for (String requiredDep : requiredDeps) {
                        if (!actualDeps.contains(requiredDep)) {
                            errors.add("竞争组 " + group.getGroupId() + " 的评审步骤 " +
                                    selectorStep.getId() + " 缺少对竞争者步骤 " + requiredDep + " 的依赖");
                        }
                    }
                }
            }
        }
        return errors;
    }

    // ==================== L2 逻辑一致性 ====================
    private List<String> validateLogic(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();
        // 1. 拓扑排序检测循环依赖
        if (hasCycle(plan.getSteps())) {
            errors.add("计划存在循环依赖");
        }
        // 2. 变量引用有效性
        errors.addAll(validateVariableRefs(plan));
        // 3. Agent 白名单
        errors.addAll(validateAgentWhitelist(plan));
        return errors;
    }

    private boolean hasCycle(List<Step> steps) { /* 现有实现 */
        return false;
    }

    private List<String> validateVariableRefs(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();
        Set<String> validRefs = plan.getSteps().stream()
                .map(s -> s.getId() + ".output")
                .collect(Collectors.toSet());
        for (Step step : plan.getSteps()) {
            if (step.getInput() != null) {
                step.getInput().values().forEach(val -> {
                    if (val instanceof String ref && ref.matches("\\{.*\\.output\\}")) {
                        String refKey = ref.substring(1, ref.length() - 1);
                        if (!validRefs.contains(refKey)) {
                            errors.add("步骤 " + step.getId() + " 引用了不存在的输出: " + refKey);
                        }
                    }
                });
            }
        }
        return errors;
    }

    private List<String> validateAgentWhitelist(OrchestrationPlan plan) {

        Set<String> availableAgents = baseNacosA2ARouter.getAvailableAgents().stream()
                .map(AgentCardWrapper::name).collect(Collectors.toSet());

        return plan.getSteps().stream()
                .filter(s -> s.getType() == Step.StepType.A2A_DELEGATE && s.getAgent() != null)
                .filter(s -> !availableAgents.contains(s.getAgent()))
                .map(s -> "步骤 " + s.getId() + " 引用了不可用的 Agent: " + s.getAgent())
                .collect(Collectors.toList());
    }

    // ==================== L3 业务语义 ====================

    /**
     * 预留后续补全 todo
     */
    private List<String> validateBusinessSemantics(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();
        // 1. 必选步骤不能缺少
        // 2. 关键操作（如发送邮件、扣款）前应有 CONFIRM 或 CREDENTIAL 检查点
        // 这可以根据任务类型或 step task 关键词判断，但属于软约束，可作为 warning
        return errors;
    }

    /**
     * 预留后续补全 todo
     */
    // ==================== L4 安全合规 ====================
    private List<String> validateSecurity(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();
        // 检查是否携带 userQuery 且 userQuery 中不应包含明文密码等（如果系统有要求）
        // 对 CREDENTIAL 检查点，验证 requiredScopes 是否在许可范围内
        return errors;
    }


    // ==================== 辅助方法 ====================
    public Step findStepById(List<Step> steps, String stepId) {
        return steps.stream().filter(s -> s.getId().equals(stepId)).findFirst().orElse(null);
    }
}

