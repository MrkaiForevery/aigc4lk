package com.air.mcp.gateway.converter;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.model.auth.ClientKeyBindRolePolicy;
import com.air.mcp.gateway.model.struct.ClientKeyBindRolePolicyStruct;
import com.air.mcp.gateway.model.struct.ClientKeyMappingStruct;
import com.alibaba.cloud.commons.lang.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientKeyBindRolePolicyConverter {

    private final JsonMapper jsonMapper;

    public List<ClientKeyBindRolePolicyStruct> toStructList(List<ClientKeyBindRolePolicy> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(this::toStruct)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public ClientKeyBindRolePolicyStruct toStruct(ClientKeyBindRolePolicy entity) {
        if (entity == null) {
            return null;
        }
        return ClientKeyBindRolePolicyStruct.builder()
                .id(entity.getId())
                .clientType(entity.getClientType())
                .clientKeyMappingStruct(
                        parseFromPg(McpSupportClientTypeEnums.getByTypeKey(entity.getClientType()), entity.getClientMappingKeyJson()))
                .bindRoleCode(entity.getBindRoleCode())
                .description(entity.getDescription())
                .delLogic(entity.getDelLogic())
                .createdBy(entity.getCreatedBy())
                .createdTime(entity.getCreatedTime())
                .updateBy(entity.getUpdateBy())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * DB 场景：PGobject → 结构体
     */
    public ClientKeyMappingStruct parseFromPg(McpSupportClientTypeEnums clientType, PGobject pgobject) {
        if (pgobject == null || StringUtils.isBlank(pgobject.getValue())) {
            return null;
        }
        return parseFromJson(clientType, pgobject.getValue());
    }

    /**
     * 入参场景：String → 结构体
     */
    public ClientKeyMappingStruct parseFromJson(McpSupportClientTypeEnums clientType, String json) {
        if (clientType == null || StringUtils.isBlank(json)) {
            return null;
        }
        try {
            ClientKeyMappingStruct struct = new ClientKeyMappingStruct();
            JsonNode rootNode = jsonMapper.readTree(json);
            switch (clientType) {
                case DIFY_AGENT_CLIENT_TYPE:
                    ClientKeyMappingStruct.DifyClientKeyStruct difyClientKeyStruct = ClientKeyMappingStruct.DifyClientKeyStruct.builder()
                            .agentId(rootNode.path("agent_id").asString())
                            .userRole(rootNode.path("user_role").asString())
                            .build();
                    struct.setDifyClientKeyStruct(difyClientKeyStruct);
                    break;
                case CODEX_AGENT_CLIENT_TYPE:
                    ClientKeyMappingStruct.CodexClientKeyStruct codexClientKeyStruct = ClientKeyMappingStruct.CodexClientKeyStruct.builder()
                            .agentId(rootNode.path("agent_id").asString())
                            .globalAuth(rootNode.path("global_auth").asBoolean())
                            .operatorAuth(rootNode.path("operator_auth").asString())
                            .build();
                    struct.setCodexClientKeyStruct(codexClientKeyStruct);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的 clientType: " + clientType);
            }
            return struct;
        } catch (Exception e) {
            throw new IllegalStateException("client_mapping_key_json 反序列化失败: " + json, e);
        }
    }
}