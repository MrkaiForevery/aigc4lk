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

    /**
     * 根据 typeKey 获取对应的枚举实例
     */
    public static McpSupportClientTypeEnums getByTypeKey(String typeKey) {
        if (typeKey == null) {
            return null;
        }
        for (McpSupportClientTypeEnums value : values()) {
            if (value.getTypeKey().equals(typeKey)) {
                return value;
            }
        }
        return null;
    }

}
