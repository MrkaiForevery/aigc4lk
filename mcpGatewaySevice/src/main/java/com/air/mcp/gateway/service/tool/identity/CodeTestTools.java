package com.air.mcp.gateway.service.tool.identity;

import com.air.mcp.gateway.annotation.meta.CustomerMcpToolMeta;
import com.air.mcp.gateway.annotation.permission.RequireMcpToolPermission;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CodeTestTools {

    @McpTool(name = "211100000_1001", description = "查询项目信息列表")
    @CustomerMcpToolMeta(domainCode = "210000000", sensitivityType = "read",elementCode = "211100000_1001")
    @RequireMcpToolPermission(toolCode = "211100000_1001",toolName = "queryProjectBaseInfoList")
    public Map<String, String> queryProjectBaseInfoList(
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
