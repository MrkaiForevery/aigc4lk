package com.air.mcp.gateway.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * mcp支持的客户端类型
 */
@Getter
@AllArgsConstructor
public enum McpSupportClientTypeEnums {
    DIFY_AGENT_CLIENT_TYPE("dify_agent_client_type","dify平台agent客户端"),
    CODEX_AGENT_CLIENT_TYPE("codex_agent_client_type","codex平台agent客户端"),
    ;

    private final String typeKey;
    private final String desc;
}
