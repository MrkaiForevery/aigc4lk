package com.air.document.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "mcp")
public class McpServersProperties {
    private List<McpServerConfig> servers;
    
    @Data
    public static class McpServerConfig {
        private String name;
        private String type;
        private String command;
        private List<String> args;
        private Map<String, String> env;
        private String url;
        private Map<String, String> headers;
        private boolean enabled = true;
    }
}