package com.air.mcp.gateway.repository;

import com.air.mcp.gateway.mapper.AgentUserRoleMappingMapper;
import com.air.mcp.gateway.model.AgentUserRoleMapping;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentUserRoleMappingRepository {

    private final AgentUserRoleMappingMapper agentUserRoleMappingMapper;

    public List<AgentUserRoleMapping> findBaseAuthRoles(String typeKey, String agentId, Set<String> userRoles) {
        LambdaQueryWrapper<AgentUserRoleMapping> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentUserRoleMapping::getMcpType, typeKey)
                .eq(AgentUserRoleMapping::getAgentId, agentId)
                .in(AgentUserRoleMapping::getUserRoleId,userRoles);
       return agentUserRoleMappingMapper.selectList(queryWrapper);
    }
}
