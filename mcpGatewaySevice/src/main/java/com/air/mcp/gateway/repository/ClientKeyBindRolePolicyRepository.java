package com.air.mcp.gateway.repository;

import com.air.mcp.gateway.mapper.AgentUserRoleMappingMapper;
import com.air.mcp.gateway.mapper.ClientKeyBindRolePolicyMapper;
import com.air.mcp.gateway.model.auth.ClientKeyBindRolePolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientKeyBindRolePolicyRepository {

    private final ClientKeyBindRolePolicyMapper clientKeyBindRolePolicyMapper;

    public List<ClientKeyBindRolePolicy> findBaseAuthRoles(String clientTypeKey, List<String> clientMappingKeyJsonList) {

        LambdaQueryWrapper<ClientKeyBindRolePolicy> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(ClientKeyBindRolePolicy::getClientType, clientTypeKey)
                .in(ClientKeyBindRolePolicy::getClientMappingKeyJson, clientMappingKeyJsonList)
                .eq(ClientKeyBindRolePolicy::getDelLogic,false);
        return clientKeyBindRolePolicyMapper.selectList(queryWrapper);
    }
}
