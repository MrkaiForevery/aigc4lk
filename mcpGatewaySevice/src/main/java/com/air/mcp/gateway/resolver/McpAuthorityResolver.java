package com.air.mcp.gateway.resolver;

import com.air.mcp.gateway.enums.McpShareTypeEnums;
import com.air.mcp.gateway.model.AgentUserRoleMapping;
import com.air.mcp.gateway.model.RolePolicyDefinition;
import com.air.mcp.gateway.registry.McpBizToolsRegistry;
import com.air.mcp.gateway.repository.AgentUserRoleMappingRepository;
import com.air.mcp.gateway.repository.RolePolicyDefinitionRepository;
import com.air.mcp.gateway.repository.UserOverrideRoleMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class McpAuthorityResolver {

    private final RolePolicyDefinitionRepository rolePolicyDefinitionRepository;
    private final AgentUserRoleMappingRepository agentUserRoleMappingRepository;
    private final UserOverrideRoleMappingRepository userOverrideRoleMappingRepository;
    private final McpBizToolsRegistry mcpBizToolsRegistry;

    /**
     * 根据agentId+userRoles+userId 解析mcpToolsRoles权限，获取对应权限范围内的可操作mcpTools
     * @param agentId 智能体id
     * @param userRoles 用户角色列表(是指在其他认证系统的权限角色)
     * @param userId 用户id
     * @return 过滤后的mcpToolsRoles列表
     */
    public Set<String> resolveMcpBizTools(String agentId, Set<String> userRoles, String userId) {
        Set<String> targetRoles = new HashSet<>();

        //获取对应的权限角色列表
        List<AgentUserRoleMapping> baseAuthRoles =  agentUserRoleMappingRepository.findBaseAuthRoles(McpShareTypeEnums.MCP_TOOLS_TYPE.getTypeKey(), agentId,userRoles);
        if (baseAuthRoles.isEmpty()) return Collections.emptySet(); // 默认拒绝
        Set<Long> roleIdSet = baseAuthRoles.stream()
                .map(AgentUserRoleMapping ::getId)
                .collect(Collectors.toSet());

        // 2. 获取所有相关role-auth策略矩阵数据
        List<RolePolicyDefinition> basePolicies = rolePolicyDefinitionRepository.findPoliciesByRoles(roleIdSet);
        if (basePolicies.isEmpty()) return Collections.emptySet();
        //剔除deny禁止项
        Set<RolePolicyDefinition> basePoliciesSet = basePolicies.stream()
                .filter(e -> !"deny".equals(e.getEffect()))
                .collect(Collectors.toSet());

        // 3. 遍历系统中的所有工具，进行标签匹配 todo
        Map<String, McpBizToolsRegistry.BizToolMetadata> allBizToolMetadataMap = mcpBizToolsRegistry.getAllBizToolMetadataMap();
        //todo 这里还需要通配符号解析的匹配逻辑


        // 4. 叠加用户级特殊覆盖 todo
//        List<UserOverrideRoleMapping> overrides = userOverrideRoleMappingRepository.findByAgentAndUser(agentId, userId);
//        for (UserOverrideRoleMapping override : overrides) {
//            if ("allow".equals(override.getEffect())) visibleTools.add(override.getToolName());
//            if ("deny".equals(override.getEffect())) visibleTools.remove(override.getToolName());
//        }

        return null;
    }

    public Set<String> resolveMcpBizResources(String agentId, Set<String> roles, String userId) {
        //todo 待实现
        return null;
    }

    public Set<String> resolveMcpBizPrompts(String agentId, Set<String> roles, String userId) {
        //todo 待实现
        return null;
    }
}
