package com.air.mcp.gateway.repository;

import com.air.mcp.gateway.mapper.RolePolicyDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RolePolicyDefinitionRepository {

    private final RolePolicyDefinitionMapper rolePolicyDefinitionMapper;

    public List<RolePolicyDefinition> findPoliciesByRoles(Set<Long> roleIdSet) {
        LambdaQueryWrapper<RolePolicyDefinition> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(RolePolicyDefinition::getId,roleIdSet);
        return rolePolicyDefinitionMapper.selectList(queryWrapper);
    }
}
