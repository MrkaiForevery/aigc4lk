package com.air.mcp.gateway.service.tool.identity;

import com.air.mcp.gateway.annotation.CustomerMcpToolMeta;
import com.air.mcp.gateway.annotation.RequireMcpToolPermission;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserBaseInfoTools {

    @McpTool(name = "query_user_base_info", description = "查询移动用户的基本信息")
    @CustomerMcpToolMeta(domainCode = "tool_identity_user_base_query", sensitivityType = "read",elementCode = "1001")
    @RequireMcpToolPermission(toolCode = "1001",toolName = "queryMobileUserBaseInfo")
    public Map<String, String> queryMobileUserBaseInfo(
            @McpToolParam(description = "用户身份ID", required = true) String userId,
            @McpToolParam(description = "系统标识", required = true) String systemId,
            McpTransportContext ctx) {
        //todo 这里先模拟返回
        return Map.of("userId", userId,
                "name", "和尚李",
                "age", "70",
                "systemId",systemId,
                "system","华强养瓜基地");
    }

}
