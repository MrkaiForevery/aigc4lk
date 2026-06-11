package com.air.commander.orchestrator;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.air.commander.configloader.properties.GraphTemplatesProperties;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 严格场景下的执行模板构建器
 */
@Component
@RequiredArgsConstructor
public class TemplatePlanGenerator {

    private final RemoteConfigLoader configLoader;

    public OrchestrationPlan loadAndPersonalize(String templateId, String userInput, MemoryContext memoryCtx) {
        Map<String, OrchestrationPlan> orchestrationPlanMap = configLoader.getGraphTemplates().stream()
                .collect(Collectors.toMap(
                        GraphTemplatesProperties.TemplateItem::getTemplateId,
                        this::toPlan
                ));
        OrchestrationPlan template = orchestrationPlanMap.get(templateId);
        if (template == null) throw new RuntimeException("Template not found: " + templateId);
        // todo 可根据 memoryCtx 调整模板，这里略
        return template;
    }

    /**
     * 将配置项转换为编排计划模型(这里只针对template这种严格场景下的执行计划匹配)
     */
    private OrchestrationPlan toPlan(GraphTemplatesProperties.TemplateItem item) {
        List<Step> steps = item.getSteps().stream().map(s -> {
            Step.StepBuilder stepBuilder = Step.builder()
                    .id(s.getId())
                    .type(Step.StepType.valueOf(s.getType()))
                    .agent(s.getAgent())
                    .task(s.getTask())
                    .mandatory(s.isMandatory())
                    .timeoutMs(s.getTimeoutMs())
                    .retry(s.getRetry())
                    .question(s.getQuestion())
                    .options(s.getOptions());

            // 解析检查点配置
            if (s.getCheckpoint() != null) {
                Step.CheckpointConfig.CheckpointType checkpointType =
                        Step.CheckpointConfig.CheckpointType.valueOf(s.getCheckpoint().getType());
                Step.CheckpointConfig checkpointConfig = Step.CheckpointConfig.builder()
                        .type(checkpointType)
                        .question(s.getCheckpoint().getQuestion())
                        .requiredScopes(s.getCheckpoint().getRequiredScopes() != null ?
                                s.getCheckpoint().getRequiredScopes() : List.of())
                        .timeoutMinutes(s.getCheckpoint().getTimeoutMinutes())
                        .build();
                stepBuilder.checkpoint(checkpointConfig);
            }

            return stepBuilder.build();
        }).collect(Collectors.toList());

        // 2. 转换回滚配置
        OrchestrationPlan.RollbackConfig rollbackConfig = null;
        if (item.getRollback() != null) {
            rollbackConfig = OrchestrationPlan.RollbackConfig.builder()
                    .enabled(item.getRollback().isEnabled())
                    .savepoint(item.getRollback().getSavepoint())
                    .build();
        }

        //todo 这里以后看看还有啥子问题
        return OrchestrationPlan.builder()
                .planId(item.getTemplateId())
                .executionMode(OrchestrationPlan.ExecutionMode.valueOf(item.getExecutionMode())) // 可扩展
                .steps(steps)
                .rollback(rollbackConfig)
                .build();
    }
}