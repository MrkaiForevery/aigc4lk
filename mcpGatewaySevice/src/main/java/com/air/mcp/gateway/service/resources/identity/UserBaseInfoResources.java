package com.air.mcp.gateway.service.resources.identity;

import com.air.mcp.gateway.annotation.CustomerMcpResourceMeta;
import com.air.mcp.gateway.annotation.RequireMcpResourcePermission;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserBaseInfoResources {

    @McpResource(uri = "db://identity/userinfo/schema", name = "用户表结构", description = "获取users表的DDL", mimeType = "text/plain")
    @CustomerMcpResourceMeta(domainId = "identity",bizDomainId = "identity_user_db",sensitivity = "read")
    @RequireMcpResourcePermission
    public String getTableUserInfoSchema(McpTransportContext ctx) {
        //这里先模拟返回 todo
        return "CREATE TABLE user_info (id BIGINT, name VARCHAR(100), ...);";
    }

}
