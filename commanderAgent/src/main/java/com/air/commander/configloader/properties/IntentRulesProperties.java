package com.air.commander.configloader.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "commander.intent-rules")
@RefreshScope
public class IntentRulesProperties {

    private List<IntentRuleItem> rules = new ArrayList<>();

    @Data
    public static class IntentRuleItem {
        private String scenario;
        private List<String> keywords;
        private int complexity;
        private boolean highRisk;

        public boolean matches(String input) {
            return keywords != null && keywords.stream().anyMatch(input::contains);
        }
    }
}