package com.air.mcp.gateway.config;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class McpJsonConfig {

    /**
     * 将通用的 JsonMapper 包装为 MCP 协议专用的 JacksonMcpJsonMapper
     */
    @Bean
    @Primary // 确保注入时优先使用这个实例
    public JacksonMcpJsonMapper jacksonMcpJsonMapper(JsonMapper jsonMapper) {
        return new JacksonMcpJsonMapper(jsonMapper);
    }
}