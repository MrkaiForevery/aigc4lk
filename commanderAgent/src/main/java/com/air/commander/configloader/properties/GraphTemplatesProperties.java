package com.air.commander.configloader.properties;

import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
}