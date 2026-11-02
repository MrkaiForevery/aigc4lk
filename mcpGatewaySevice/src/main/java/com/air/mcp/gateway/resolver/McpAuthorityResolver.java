package com.air.mcp.gateway.resolver;

import com.air.mcp.gateway.auth.PermissionSearchEngine;
import com.air.mcp.gateway.auth.handler.AbstractBlackListParserHandler;
import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.enums.McpSupportShareTypeEnums;
import com.air.mcp.gateway.model.auth.AuthBlackListMapping;
import com.air.mcp.gateway.model.auth.ClientKeyBindRolePolicy;
import com.air.mcp.gateway.model.auth.RoleBindElementPolicy;
import com.air.mcp.gateway.model.identity.AuthMcpCommonInfo;
import com.air.mcp.gateway.registry.McpBizToolsRegistry;
import com.air.mcp.gateway.repository.ClientKeyBindRolePolicyRepository;
import com.air.mcp.gateway.repository.RoleBindElementPolicyRepository;
import com.air.mcp.gateway.repository.AuthBlackListMappingRepository;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class McpAuthorityResolver {

    private final RoleBindElementPolicyRepository roleBindElementPolicyRepository;
    private final ClientKeyBindRolePolicyRepository clientKeyBindRolePolicyRepository;
    private final AuthBlackListMappingRepository authBlackListMappingRepository;

    private final PermissionSearchEngine permissionSearchEngine;

    public Set<String> resolveAuthElements(AuthMcpCommonInfo authInfo, McpSupportShareTypeEnums supportShareTypeEnums) {
        if (McpSupportShareTypeEnums.equalsByTypeKey(McpSupportShareTypeEnums.MCP_TOOLS_TYPE,supportShareTypeEnums)){
           return resolveMcpBizTools(authInfo);
        }

        if (McpSupportShareTypeEnums.equalsByTypeKey(McpSupportShareTypeEnums.MCP_RESOURCES_TYPE,supportShareTypeEnums)){
            return resolveMcpBizResources(authInfo);
        }

        if (McpSupportShareTypeEnums.equalsByTypeKey(McpSupportShareTypeEnums.MCP_PROMPT_TYPE,supportShareTypeEnums)){
            return resolveMcpBizPrompts(authInfo);
        }

        return Set.of();
    }

    /**
     * 根据commonInfo 解析mcpToolsRoles权限，获取对应权限范围内的可操作mcpTools
     * @return 过滤后的mcpToolsRoles列表
     */
    private Set<String> resolveMcpBizTools(AuthMcpCommonInfo commonInfo) {
        //获取对应的权限角色列表
        List<ClientKeyBindRolePolicy> baseAuthRoles =  clientKeyBindRolePolicyRepository.findBaseAuthRoles(commonInfo.getClientTypeEnum().getTypeKey(), commonInfo.getClientMappingKeyJsonList());
        if (baseAuthRoles.isEmpty()) return Collections.emptySet(); // 默认拒绝
        Set<String> roleCodeSet = baseAuthRoles.stream()
                .map(ClientKeyBindRolePolicy ::getBindRoleCode)
                .collect(Collectors.toSet());

        // 获取所有相关role-auth策略矩阵数据
        List<RoleBindElementPolicy> basePolicies = roleBindElementPolicyRepository.findPoliciesByRoles(roleCodeSet);
        if (basePolicies.isEmpty()) return Collections.emptySet();
        Set<String> baseElementCodeSet = basePolicies.stream()
                .map(RoleBindElementPolicy::getElementCode)
                .collect(Collectors.toSet());

        //用户黑名单过滤
        List<AuthBlackListMapping> allBaseBlackList = authBlackListMappingRepository.findAllBlackListByClientType(commonInfo.getClientTypeEnum().getTypeKey());
        //这里要写一个黑名单处理逻辑
        AbstractBlackListParserHandler blackListHandler = permissionSearchEngine.findBlackListHandlerByClientKeyEnum(commonInfo.getClientTypeEnum());
        Map<String, List<String>> allBaseBlackMap = blackListHandler.parserBlackList(allBaseBlackList);

        //传入本身userId,过滤掉黑名单里面的权限
        List<String> userIdJsonList = commonInfo.getBlackMappingKeyJsonList();
        Set<String> resultSet = blackListHandler.doFilter(userIdJsonList, allBaseBlackMap, baseElementCodeSet);

        return resultSet;
    }

    private Set<String> resolveMcpBizResources(AuthMcpCommonInfo commonInfo) {
        //todo 待实现
        return null;
    }

    private Set<String> resolveMcpBizPrompts(AuthMcpCommonInfo commonInfo) {
        //todo 待实现
        return null;
    }
}
