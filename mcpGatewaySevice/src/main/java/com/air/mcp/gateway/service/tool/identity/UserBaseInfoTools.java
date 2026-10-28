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

    @McpTool(name = "query_user_base_info", description = "查询用户的基本信息")
    @CustomerMcpToolMeta(domainId = "identity", bizDomainId = "identity_user_0001", sensitivity = "read")
    @RequireMcpToolPermission
    public Map<String, String> queryUserBaseInfo(
            @McpToolParam(description = "用户身份令牌", required = true) String userToken,
            @McpToolParam(description = "系统标识", required = true) String systemId,
            McpTransportContext ctx) {
        //todo 这里先模拟返回
        return Map.of("userId", "950925",
                "name", "和尚李",
                "age", "70",
                "system","华强养瓜基地");
    }

}
