package com.air.commander.configloader.properties;

import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "commander.graph-templates")
@RefreshScope
public class GraphTemplatesProperties {

    private List<TemplateItem> templates = new ArrayList<>();

    @Data
    public static class TemplateItem {
        private String templateId;
        private String name;
        private String executionMode;
        private List<String> triggerScenarios;
        private List<String> triggerKeywords;
        private List<StepItem> steps;
        private RollbackConfig rollback;

        @Data
        public static class StepItem {
            private String id;
            private String type;        // A2A_DELEGATE, INTERRUPT, LLM_CALL
            private String agent;
            private String task;
            private boolean mandatory;
            private long timeoutMs;
            private int retry;
            private String question;
            private List<String> options;
            // 新增：检查点配置
            private CheckpointItem checkpoint;

            @Data
            public static class CheckpointItem {
                private String type;                // CREDENTIAL 或 CONFIRM
                private String question;
                private List<String> requiredScopes;
                private int timeoutMinutes = 30;    // 默认30分钟
            }
        }

        @Data
        public static class RollbackConfig {
            private boolean enabled;
            private String savepoint;
        }
    }

    /**
     * 将配置项转换为编排计划模型(这里只针对template这种严格场景下的执行计划匹配)
     */
    public OrchestrationPlan toPlan(TemplateItem item) {
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