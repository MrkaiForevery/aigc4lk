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
public class DifyAgentAuthParams {

    /**
     * 客户端类型
     */
    private String clientType;

    /**
     * agent 标识唯一id
     */
    private String agentId;

    /**
     * 用户 标识唯一id
     */
    private String userId;

    /**
     * 用户角色组集合
     */
    private List<String> authRoles;
}