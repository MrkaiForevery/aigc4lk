package com.air.mcp.gateway.service.tool.identity;

import com.air.mcp.gateway.annotation.meta.CustomerMcpToolMeta;
import com.air.mcp.gateway.annotation.permission.RequireMcpToolPermission;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @McpTool中的name必须全局唯一
 */
@Service
@RequiredArgsConstructor
public class UserBaseInfoTools {

    /**
     *  这里要实现权限绑定必须满足：
     *  @McpTool(name = "111000000_1001") ==  @CustomerMcpToolMeta(elementCode = "111000000_1001") == @RequireMcpToolPermission(toolCode = "111000000_1001")
     * @param userId
     * @param systemId
     * @param ctx
     * @return
     */
    @McpTool(name = "query_mobile_user_base_info", description = "查询移动用户的基本信息")
    @CustomerMcpToolMeta(domainCode = "111000000", sensitivityType = "read",elementCode = "query_mobile_user_base_info")
    @RequireMcpToolPermission(toolCode = "query_mobile_user_base_info",toolName = "queryMobileUserBaseInfo")
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
