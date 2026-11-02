package com.air.mcp.gateway.auth.handler;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.model.identity.AuthMcpCommonInfo;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.servlet.http.HttpServletRequest;


public abstract class AbstractClientAuthHandler {

    public abstract McpTransportContext extractAuthElementToMcpContext(HttpServletRequest request);

    public abstract AuthMcpCommonInfo buildMcpContextAuthObject(McpTransportContext mcpTransportContext);

    public abstract McpSupportClientTypeEnums getHandleClientType();
}
