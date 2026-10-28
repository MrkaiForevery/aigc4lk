package com.air.mcp.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMcpInfo {

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
    private Set<String> userRoles;
}
