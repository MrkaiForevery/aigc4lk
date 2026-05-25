package com.air.platform.common.mcp.client;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class McpClientManager {
    
    @Value("${spring.ai.mcp.client.request-timeout:60s}")
    private String requestTimeout;
    
    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, McpToolInfo> toolCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("MCP Client Manager initialized with timeout: {}", requestTimeout);
    }
    
    /**
     * 注册MCP连接
     */
    public void registerConnection(String serverName, McpConnectionConfig config) {
        McpConnection connection = McpConnection.builder()
            .serverName(serverName)
            .url(config.getUrl())
            .type(config.getType())
            .capabilities(config.getCapabilities())
            .status(ConnectionStatus.CONNECTING)
            .build();
        
        connections.put(serverName, connection);
        log.info("Registered MCP connection: {} ({})", serverName, config.getUrl());
        
        // 异步建立连接
        establishConnection(serverName, connection);
    }
    
    /**
     * 调用MCP工具
     */
    public McpToolResponse callTool(String serverName, String toolName, Map<String, Object> arguments) {
        McpConnection connection = connections.get(serverName);
        if (connection == null) {
            throw new McpException("MCP server not found: " + serverName);
        }
        
        if (connection.getStatus() != ConnectionStatus.CONNECTED) {
            throw new McpException("MCP server not connected: " + serverName);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 实际实现：通过HTTP/gRPC调用MCP服务
            Object result = doCallTool(connection, toolName, arguments);
            
            return McpToolResponse.builder()
                .success(true)
                .result(result)
                .durationMs(System.currentTimeMillis() - startTime)
                .serverName(serverName)
                .toolName(toolName)
                .build();
                
        } catch (Exception e) {
            log.error("MCP tool call failed: {}.{}", serverName, toolName, e);
            return McpToolResponse.builder()
                .success(false)
                .error(e.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .serverName(serverName)
                .toolName(toolName)
                .build();
        }
    }
    
    /**
     * 获取工具信息
     */
    public McpToolInfo getToolInfo(String serverName, String toolName) {
        String cacheKey = serverName + ":" + toolName;
        return toolCache.computeIfAbsent(cacheKey, k -> fetchToolInfo(serverName, toolName));
    }
    
    /**
     * 根据能力发现MCP服务器
     */
    public List<McpConnection> discoverByCapability(String capability) {
        return connections.values().stream()
            .filter(conn -> conn.getCapabilities() != null && 
                   conn.getCapabilities().contains(capability))
            .filter(conn -> conn.getStatus() == ConnectionStatus.CONNECTED)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有已连接的MCP服务器
     */
    public List<McpConnection> getAllConnected() {
        return connections.values().stream()
            .filter(conn -> conn.getStatus() == ConnectionStatus.CONNECTED)
            .collect(Collectors.toList());
    }
    
    private void establishConnection(String serverName, McpConnection connection) {
        // 实际实现：建立HTTP/SSE连接
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000); // 模拟连接过程
                connection.setStatus(ConnectionStatus.CONNECTED);
                log.info("MCP connection established: {}", serverName);
            } catch (Exception e) {
                connection.setStatus(ConnectionStatus.ERROR);
                connection.setErrorMessage(e.getMessage());
                log.error("Failed to establish MCP connection: {}", serverName, e);
            }
        });
    }
    
    private Object doCallTool(McpConnection connection, String toolName, Map<String, Object> arguments) {
        // 实际实现：发送HTTP请求到MCP服务
        log.debug("Calling MCP tool: {}.{} with args: {}", 
            connection.getServerName(), toolName, arguments);
        return Map.of("result", "success");
    }
    
    private McpToolInfo fetchToolInfo(String serverName, String toolName) {
        // 实际实现：从MCP服务获取工具Schema
        return McpToolInfo.builder()
            .serverName(serverName)
            .toolName(toolName)
            .description("Tool description")
            .inputSchema(Map.of())
            .build();
    }
    
    @PreDestroy
    public void destroy() {
        connections.values().forEach(conn -> conn.setStatus(ConnectionStatus.DISCONNECTED));
        log.info("MCP Client Manager destroyed");
    }
    
    // ==================== 内部类 ====================
    
    @Data
    @Builder
    public static class McpConnection {
        private String serverName;
        private String url;
        private ConnectionType type;
        private List<String> capabilities;
        private ConnectionStatus status;
        private String errorMessage;
        private long connectedAt;
    }
    
    @Data
    @Builder
    public static class McpConnectionConfig {
        private String url;
        private ConnectionType type;
        private List<String> capabilities;
        private Map<String, String> headers;
        private Integer timeoutSeconds;
    }
    
    @Data
    @Builder
    public static class McpToolResponse {
        private boolean success;
        private Object result;
        private String error;
        private long durationMs;
        private String serverName;
        private String toolName;
    }
    
    @Data
    @Builder
    public static class McpToolInfo {
        private String serverName;
        private String toolName;
        private String description;
        private Map<String, Object> inputSchema;
        private Map<String, Object> outputSchema;
    }
    
    public enum ConnectionType {
        HTTP, SSE, GRPC, STDIO
    }
    
    public enum ConnectionStatus {
        CONNECTING, CONNECTED, ERROR, DISCONNECTED
    }
    
    public static class McpException extends RuntimeException {
        public McpException(String message) {
            super(message);
        }
        public McpException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}