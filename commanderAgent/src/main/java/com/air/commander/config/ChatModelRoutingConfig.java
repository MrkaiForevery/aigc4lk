package com.air.commander.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型切换配置数据，从Nacos配置中心platform-model-routing-config中读取
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.air")
public class ChatModelRoutingConfig {

    private List<CommanderModelDefinition> models = new ArrayList<>();

    @Data
    public static class CommanderModelDefinition {
        private String modelId;
        private String type;          // DASHSCOPE / OPENAI_COMPATIBLE
        private String provider;
        private String modelName;
        private List<String> capabilities = new ArrayList<>();
        private Integer weight;
        private boolean enabled;
    }
}