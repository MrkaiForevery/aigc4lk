package com.air.customerServiceAgent.mcp;

import cn.hutool.core.util.ObjectUtil;
import com.air.customerServiceAgent.config.McpServersProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RefreshScope
public class RemoteMcpToolProvider implements ToolCallbackProvider {

    private final McpServersProperties mcpServersProperties;

    private final Environment environment;
    private final ObjectMapper objectMapper;

    // 管理当前所有激活的同步客户端
    private final List<McpSyncClient> activeClients = new CopyOnWriteArrayList<>();
    private final List<ToolCallbackProvider> toolProviders = new CopyOnWriteArrayList<>();

    public RemoteMcpToolProvider(Environment environment,
                                 ObjectMapper objectMapper,
                                 McpServersProperties  mcpServersProperties) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.mcpServersProperties = mcpServersProperties;
    }

    @PostConstruct
    public void init() {
        refreshMcpClients();
    }

    /**
     * 刷新所有 MCP 客户端（从 Nacos 配置重新构建）
     */
    public void refreshMcpClients() {
        log.info("Refreshing MCP clients from Nacos config...");
        // 1. 关闭旧客户端
        closeOldClients();
        // 2. 解析新配置
        List<McpServersProperties.McpServerConfig> serversConfigs = mcpServersProperties.getServers();
        if (ObjectUtil.isEmpty(serversConfigs)) {
            log.warn("No MCP servers configured or invalid JSON, using empty provider");
            // 没有配置时，使用空的 ToolCallbackProvider
            toolProviders.clear();
            return;
        }

        // 3. 为每个启用的 server 创建客户端
        List<ToolCallbackProvider> providers = new ArrayList<>();
        for (McpServersProperties.McpServerConfig serverConfig : serversConfigs) {
            if (!serverConfig.isEnabled()) {
                log.info("MCP server '{}' is disabled, skipping", serverConfig.getName());
                continue;
            }
            try {
                McpSyncClient client = createMcpSyncClient(serverConfig);
                activeClients.add(client);
                // 🔥 关键修正：使用 SyncMcpToolCallbackProvider 进行包装
                ToolCallbackProvider provider = new SyncMcpToolCallbackProvider(client);
                toolProviders.add(provider);
                log.info("MCP server '{}' initialized successfully", serverConfig.getName());
            } catch (Exception e) {
                log.error("Failed to initialize MCP server '{}': {}", serverConfig.getName(), e.getMessage(), e);
            }
        }
    }

    private McpSyncClient createMcpSyncClient(McpServersProperties.McpServerConfig config) {
        String type = config.getType();
        McpClientTransport transport = createTransport(config);
        // 使用 McpClient.sync 和 build() 来创建 McpSyncClient，这样就有 closeGracefully() 方法了
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .build();
    }

    private McpClientTransport createTransport(McpServersProperties.McpServerConfig config) {
        if ("stdio".equalsIgnoreCase(config.getType())) {
            // 通过 StdioTransport.Builder 构建传输层
            ServerParameters.Builder paramsBuilder = ServerParameters.builder(config.getCommand());
            if (config.getArgs() != null && !config.getArgs().isEmpty()) {
                paramsBuilder.args(config.getArgs());
            }
            // 处理环境变量
            Map<String, String> env = new HashMap<>();
            if (config.getEnv() != null) {
                config.getEnv().forEach((k, v) -> env.put(k, resolvePlaceholders(v)));
            }
            ServerParameters params = paramsBuilder.build();
            // 创建 StdioClientTransport 实例
            JacksonMcpJsonMapper jacksonMcpJsonMapper = new JacksonMcpJsonMapper(objectMapper);
            return new StdioClientTransport(params,jacksonMcpJsonMapper);
        } else if ("streamable-http".equalsIgnoreCase(config.getType())) {
            // 构建 StreamableHttpTransport
            return HttpClientStreamableHttpTransport.builder(resolvePlaceholders(config.getUrl())).build();
        } else {
            throw new IllegalArgumentException("Unsupported MCP transport type: " + config.getType());
        }
    }

    private String resolvePlaceholders(String value) {
        if (value == null) return null;
        return environment.resolvePlaceholders(value);
    }

    private void closeOldClients() {
        for (McpSyncClient client : activeClients) {
            try {
                client.closeGracefully();
                log.debug("Closed MCP sync client gracefully");
            } catch (Exception e) {
                log.warn("Error gracefully closing MCP client, attempting force close", e);
                try {
                    client.close();
                } catch (Exception ex) {
                    log.error("Failed to close MCP client", ex);
                }
            }
        }
        activeClients.clear();
    }


    @Override
    public ToolCallback[] getToolCallbacks() {
        return toolProviders.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toArray(ToolCallback[]::new);
    }

    @PreDestroy
    public void destroy() {
        closeOldClients();
    }

}