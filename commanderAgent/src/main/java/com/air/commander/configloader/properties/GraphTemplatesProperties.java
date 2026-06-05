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
        private String mode;
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
        }

        @Data
        public static class RollbackConfig {
            private boolean enabled;
            private String savepoint;
        }
    }

    // 转换为原有的 OrchestrationPlan 结构
    public OrchestrationPlan toPlan(TemplateItem item) {
        List<Step> steps = item.getSteps().stream().map(s -> Step.builder()
                .id(s.getId())
                .type(Step.StepType.valueOf(s.getType()))
                .agent(s.getAgent())
                .task(s.getTask())
                .mandatory(s.isMandatory())
                .timeoutMs(s.getTimeoutMs())
                .retry(s.getRetry())
                .question(s.getQuestion())
                .options(s.getOptions())
                .build()
        ).collect(Collectors.toList());

        return OrchestrationPlan.builder()
                .planId(item.getTemplateId())
                .executionMode(OrchestrationPlan.ExecutionMode.SEQUENTIAL) // 可扩展
                .steps(steps)
                .build();
    }
}