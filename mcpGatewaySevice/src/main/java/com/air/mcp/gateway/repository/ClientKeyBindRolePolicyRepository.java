package com.air.mcp.gateway.repository;

import com.air.mcp.gateway.mapper.ClientKeyBindRolePolicyMapper;
import com.air.mcp.gateway.model.auth.ClientKeyBindRolePolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientKeyBindRolePolicyRepository {

    private final ClientKeyBindRolePolicyMapper clientKeyBindRolePolicyMapper;

    public List<ClientKeyBindRolePolicy> findBaseAuthRoles(String clientTypeKey, List<String> clientMappingKeyJsonList) {

        LambdaQueryWrapper<ClientKeyBindRolePolicy> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(ClientKeyBindRolePolicy::getClientType, clientTypeKey)
                .apply("del_logic = B'0'");

        // 利用 apply 拼接 JSONB 的包含操作符
        if (CollectionUtils.isNotEmpty(clientMappingKeyJsonList)) {
            // 将 list 转换为 SQL 数组格式: ARRAY['{"a":1}'::jsonb, '{"b":2}'::jsonb]
            String jsonArrayStr = clientMappingKeyJsonList.stream()
                    .map(json -> "'" + json + "'::jsonb")
                    .collect(Collectors.joining(",", "ARRAY[", "]"));

            // 使用 = ANY 匹配数组中的任意一个
            queryWrapper.apply("client_mapping_key_json @> ANY(" + jsonArrayStr + ")");
        }

        return clientKeyBindRolePolicyMapper.selectList(queryWrapper);
    }
}
