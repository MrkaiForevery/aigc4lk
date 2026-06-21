package com.air.document.skills;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "document-agent")
public class AgentSkillsProperties {
    private String name;
    private String description;
    private List<SkillConfig> skills;

    @Data
    public static class SkillConfig {
        private String skillId;
        private String description;
        // 可选，用于未来扩展，如标签、版本等
        private List<String> tags;
    }
}