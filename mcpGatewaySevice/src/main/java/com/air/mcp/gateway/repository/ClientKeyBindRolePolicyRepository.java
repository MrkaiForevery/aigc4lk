package com.air.mcp.gateway.repository;

import com.air.mcp.gateway.converter.ClientKeyBindRolePolicyConverter;
import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.mapper.ClientKeyBindRolePolicyMapper;
import com.air.mcp.gateway.matcher.ClientKeyMappingMatcher;
import com.air.mcp.gateway.model.auth.ClientKeyBindRolePolicy;
import com.air.mcp.gateway.model.bo.ClientKeyBindRolePolicyBO;
import com.air.mcp.gateway.model.bo.ClientKeyMappingStruct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientKeyBindRolePolicyRepository {

    private final ClientKeyBindRolePolicyMapper clientKeyBindRolePolicyMapper;
    private final ClientKeyBindRolePolicyConverter converter;
    private final ClientKeyMappingMatcher matcher;

    public List<ClientKeyBindRolePolicyBO> findBaseAuthRoles(String clientTypeKey, List<String> clientMappingKeyJsonListParam) {
        McpSupportClientTypeEnums clientTypeEnum = McpSupportClientTypeEnums.getByTypeKey(clientTypeKey);

        LambdaQueryWrapper<ClientKeyBindRolePolicy> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ClientKeyBindRolePolicy::getClientType, clientTypeKey)
                .apply("del_logic = B'0'");

        //获取这个客户端对应的所有的roles
        List<ClientKeyBindRolePolicy> clientAllRoles = clientKeyBindRolePolicyMapper.selectList(queryWrapper);

        //标准roles转化成bo对象
        List<ClientKeyBindRolePolicyBO> clientAllRoleBOS = converter.toBOList(clientAllRoles);

        // 入参 JSON 转结构体
        List<ClientKeyMappingStruct> paramStructs = clientMappingKeyJsonListParam == null
                ? Collections.emptyList()
                : clientMappingKeyJsonListParam.stream()
                .filter(StringUtils::isNotBlank)
                .map(json -> converter.parseFromJson(clientTypeEnum, json))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        //过滤匹配权限
        List<ClientKeyBindRolePolicyBO> resultBOS = matcher.filterMatchRoles(clientTypeEnum, clientAllRoleBOS, paramStructs);

        return resultBOS;
    }
}
