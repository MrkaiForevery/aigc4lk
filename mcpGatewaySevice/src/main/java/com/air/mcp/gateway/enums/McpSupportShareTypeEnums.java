package com.air.mcp.gateway.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * mcp分享类型枚举
 */
@Getter
@AllArgsConstructor
public enum McpSupportShareTypeEnums {
    MCP_TOOLS_TYPE("mcp_tools_type","mcp工具类型"),
    MCP_RESOURCES_TYPE("mcp_resources_type","mcp资源类型"),
    MCP_PROMPT_TYPE("mcp_prompt_type","mcp提示词类型"),
    ;

    private final String typeKey;
    private final String desc;

    public static boolean equalsByTypeKey(McpSupportShareTypeEnums a,McpSupportShareTypeEnums b){
        if (a == null || b == null) {
            return false;
        }
        if ( a.getTypeKey().equals(b.getTypeKey())) {
            return true;
        }
        return false;
    }
}
