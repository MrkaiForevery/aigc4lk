package com.air.mcp.gateway.auth.handler;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.enums.McpSupportShareTypeEnums;
import com.air.mcp.gateway.model.identity.AuthMcpCommonInfo;
import com.air.mcp.gateway.parser.IdentityTokenParser;
import com.air.mcp.gateway.resolver.McpAuthorityResolver;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DifyClientAuthHandler extends AbstractClientAuthHandler {

    private static final McpSupportClientTypeEnums clientTypeEnum = McpSupportClientTypeEnums.DIFY_AGENT_CLIENT_TYPE;

    private final JsonMapper jsonMapper;
    private final IdentityTokenParser identityTokenParser;
    private final McpAuthorityResolver mcpAuthorityResolver;

    @Override
    public McpTransportContext extractAuthElementToMcpContext(HttpServletRequest request) {
        //提取 Header里面的参数
        String clientType = request.getHeader("X-Client-Type");
        String agentId = request.getHeader("X-Agent-ID");
        String token = request.getHeader("X-Dify-SSO-Token");
        //校验 Token 并解析角色
        Set<String> userRoles = identityTokenParser.parseDifyUserTokenRoles(token);
        String userId = identityTokenParser.parseDifyUserId(token);
        return McpTransportContext.create(Map.of(
                "clientType",clientType,
                "agentId", agentId != null ? agentId : "unknown",
                "userId", userId,
                "userRoles", userRoles
        ));
    }

    @Override
    public AuthMcpCommonInfo buildMcpContextAuthObject(McpTransportContext mcpTransportContext) {
        // 从 Context 中读取由 contextExtractor 放入的身份信息
        String agentId = (String) mcpTransportContext.get("agentId");
        String userId = (String) mcpTransportContext.get("userId");
        Set<String> userRoles = (Set<String>) mcpTransportContext.get("userRoles");

        //构造黑名单检查keyList
        List<String> userIdList = List.of(userId);
        Map<String, Object> userIdMap = Map.of("user_Id_list", userIdList);
        String blackUserIdCheckJson = jsonMapper.writeValueAsString(userIdMap);
        List<String> blackJsonList = new ArrayList<>();
        blackJsonList.add(blackUserIdCheckJson);

        //构造客户端映射权限roleKeyList
        List<String> roleKeyJsonList = new ArrayList<>();
        userRoles.forEach(role -> {
            Map<String, String> roleMap = Map.of("agent_id", agentId, "user_role", role);
            String roleJson = jsonMapper.writeValueAsString(roleMap);
            roleKeyJsonList.add(roleJson);
        });

        return AuthMcpCommonInfo.builder()
                .clientTypeEnum(getHandleClientType())
                .blackMappingKeyJsonList(blackJsonList)
                .clientMappingKeyJsonList(roleKeyJsonList)
                .build();
    }

    @Override
    public Set<String> searchPermissionList(McpSupportShareTypeEnums shareTypeEnums, AuthMcpCommonInfo commonInfo) {
        boolean toolFlag = McpSupportShareTypeEnums.equalsByTypeKey(McpSupportShareTypeEnums.MCP_TOOLS_TYPE, shareTypeEnums);
        boolean resourceFlag = McpSupportShareTypeEnums.equalsByTypeKey(McpSupportShareTypeEnums.MCP_RESOURCES_TYPE, shareTypeEnums);
        boolean promptFlag = McpSupportShareTypeEnums.equalsByTypeKey(McpSupportShareTypeEnums.MCP_PROMPT_TYPE, shareTypeEnums);
        if (toolFlag){
           return mcpAuthorityResolver.resolveMcpBizTools(commonInfo);
        }

        if (resourceFlag){
            return mcpAuthorityResolver.resolveMcpBizResources(commonInfo);
        }

        if (promptFlag){
            return mcpAuthorityResolver.resolveMcpBizPrompts(commonInfo);
        }

        return Set.of();
    }



    @Override
    public McpSupportClientTypeEnums getHandleClientType() {
        return this.clientTypeEnum;
    }
}
