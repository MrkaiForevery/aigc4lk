package com.air.mcp.gateway.matcher;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.model.struct.ClientKeyBindRolePolicyStruct;
import com.air.mcp.gateway.model.struct.ClientKeyMappingStruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ClientKeyMappingMatcher {

    /**
     * 从 BO 列表里筛出至少匹配一个 param 的记录
     */
    public List<ClientKeyBindRolePolicyStruct> filterMatchRoles(McpSupportClientTypeEnums clientType,
                                                                List<ClientKeyBindRolePolicyStruct> candidates,
                                                                List<ClientKeyMappingStruct> params) {
        if (CollectionUtils.isEmpty(candidates) || CollectionUtils.isEmpty(params)) {
            return Collections.emptyList();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(bo -> {
                    ClientKeyMappingStruct standard = bo.getClientKeyMappingStruct();
                    return standard != null
                            && params.stream().anyMatch(param -> doKeyMatch(clientType, standard, param));
                })
                .collect(Collectors.toList());
    }

    public boolean doKeyMatch(McpSupportClientTypeEnums clientType,
                              ClientKeyMappingStruct standard,
                              ClientKeyMappingStruct param) {
        if (clientType == null || standard == null || param == null) {
            return false;
        }
        switch (clientType) {
            case DIFY_AGENT_CLIENT_TYPE: {
                ClientKeyMappingStruct.DifyClientKeyStruct s = standard.getDifyClientKeyStruct();
                ClientKeyMappingStruct.DifyClientKeyStruct p = param.getDifyClientKeyStruct();
                return s != null && p != null
                        && Objects.equals(s.getAgentId(), p.getAgentId())
                        && Objects.equals(s.getUserRole(), p.getUserRole());
            }
            case CODEX_AGENT_CLIENT_TYPE: {
                ClientKeyMappingStruct.CodexClientKeyStruct s = standard.getCodexClientKeyStruct();
                ClientKeyMappingStruct.CodexClientKeyStruct p = param.getCodexClientKeyStruct();
                return s != null && p != null
                        && Objects.equals(s.getAgentId(), p.getAgentId())
                        && Objects.equals(s.getOperatorAuth(), p.getOperatorAuth())
                        && Objects.equals(s.getGlobalAuth(), p.getGlobalAuth());
            }
            default:
                return false;
        }
    }
}