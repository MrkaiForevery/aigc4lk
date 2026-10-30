package com.air.mcp.gateway.repository;

import com.air.mcp.gateway.mapper.RoleBindElementPolicyMapper;
import com.air.mcp.gateway.model.auth.RoleBindElementPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleBindElementPolicyRepository {

    private final RoleBindElementPolicyMapper roleBindElementPolicyMapper;

    public List<RoleBindElementPolicy> findPoliciesByRoles(Set<String> roleCodeSet) {
        LambdaQueryWrapper<RoleBindElementPolicy> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(RoleBindElementPolicy::getRoleCode,roleCodeSet)
                .eq(RoleBindElementPolicy::getDelLogic,false);
        return roleBindElementPolicyMapper.selectList(queryWrapper);
    }
}
