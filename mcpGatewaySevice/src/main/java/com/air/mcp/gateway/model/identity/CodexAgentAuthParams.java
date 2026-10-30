package com.air.mcp.gateway.model.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodexAgentAuthParams {

    /**
     * 客户端类型
     */
    private String clientType;

    /**
     * agent 标识唯一id
     */
    private String agentCode;

    /**
     * 用户 认证权限code
     */
    private List<String> authRoles;
}