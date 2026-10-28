package com.air.mcp.gateway.config;

import com.air.mcp.gateway.parser.IdentityTokenParser;
import com.air.mcp.gateway.resolver.McpAuthorityResolver;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 这里spring ai 框架只提供了对tool类型的隐藏和过滤行为，根本原因如下：
 * MCP协议规范为三种原语定义了明确的控制主体，这决定了它们的风险等级完全不同：
 * Tools	模型 (Model)	LLM自主决定是否以及何时调用	高风险。模型可自主发起行动，可能产生副作用（如发邮件、删数据），必须严格管控其可见范围。
 * Resources	应用 (Application)	宿主应用决定将哪些资源作为上下文注入给模型	低风险。只读数据，模型无法主动“拉取”，由应用层控制，暴露风险可控。
 * Prompts	用户 (User)	人类用户主动从菜单中选择并触发	低风险。由人控制，是面向用户的快捷方式，而非模型的自主行为。
 */
@Configuration
public class McpGatewayConfig {

    /**
     * 按权限视角，返回客户端可见tools列表
     * Spring AI 2.0.2（底层MCP Java SDK 2.1.0）中，只有 tools/list 提供了过滤能力，而 Resource 和 Prompt 没有
     * @param mcpAuthorityResolver
     * @return
     */
    @Primary
    @Bean
    public McpSyncServerCustomizer toolVisibilityCustomizer(McpAuthorityResolver mcpAuthorityResolver, List<McpServerFeatures.SyncResourceSpecification> resourceSpecs) {
        return builder -> builder
                .addToolFilter((transportContext, tool) -> {
                    // 从 Context 中读取由 contextExtractor 放入的身份信息
                    String agentId = (String) transportContext.get("agentId");
                    String userId = (String) transportContext.get("userId");
                    Set<String> userRoles = (Set<String>) transportContext.get("userRoles");
                    if (agentId == null || userId == null) return false;
                    //tools类型处理
                    Set<String> visibleTools = mcpAuthorityResolver.resolveMcpBizTools(agentId, userRoles, userId);
                    return visibleTools.contains(tool.name());
                });
    }

    /**
     * 传输层拦截定制化处理逻辑: 将request-header中的权限信息传递给McpTransportContext上下文中
     * @param identityTokenParser
     * @return
     */
    @Bean
    public WebMvcStreamableServerTransportProvider transportProvider(IdentityTokenParser identityTokenParser) {
        return WebMvcStreamableServerTransportProvider.builder()
                .contextExtractor(serverRequest -> {
                    var request = serverRequest.servletRequest();
                    // 1. 提取 Header
                    String agentId = request.getHeader("X-Agent-ID");
                    String token = request.getHeader("X-Dify-SSO-Token");
                    // 2. 校验 Token 并解析角色
                    Set<String> userRoles = identityTokenParser.parseUserTokenRoles(token);
                    String userId = identityTokenParser.parseUserId(token);
                    // 3. 放入mcp的上下文
                    return McpTransportContext.create(Map.of(
                            "agentId", agentId != null ? agentId : "unknown",
                            "userId", userId,
                            "userRoles", userRoles
                    ));
                }).build();
    }
}
