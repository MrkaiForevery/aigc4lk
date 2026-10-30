package com.air.mcp.gateway.repository;

import com.air.mcp.gateway.mapper.AuthBlackListMappingMapper;
import com.air.mcp.gateway.mapper.RoleBindElementPolicyMapper;
import com.air.mcp.gateway.model.auth.AuthBlackListMapping;
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
public class AuthBlackListMappingRepository {

    private final AuthBlackListMappingMapper authBlackListMappingMapper;

    public List<AuthBlackListMapping> findAllBlackListByClientType(String clientTypeKey) {
        LambdaQueryWrapper<AuthBlackListMapping> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(AuthBlackListMapping::getClientType,clientTypeKey)
                .eq(AuthBlackListMapping::getDelLogic,false)
                .eq(AuthBlackListMapping::getEffectFlag,true);
        return authBlackListMappingMapper.selectList(queryWrapper);
    }
}
