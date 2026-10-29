package com.air.mcp.gateway.model.identity;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMcpCommonInfo {

    /**
     * 客户端类型
     */
    private McpSupportClientTypeEnums clientTypeEnum;

    private DifyAgentAuthParams difyAgentAuthParams;

    private CodexAgentAuthParams codexAgentAuthParams;


    public static class DifyAgentAuthParams{
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

    public static class CodexAgentAuthParams{

        /**
         * agent 标识唯一id
         */
        private String agentId;

        /**
         * 用户 认证权限code
         */
        private String userAuthCode;
    }
}

