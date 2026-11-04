package com.air.mcp.gateway.config;

import com.air.mcp.gateway.auth.PermissionSearchEngine;
import com.air.mcp.gateway.auth.handler.AbstractClientAuthHandler;
import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.enums.McpSupportShareTypeEnums;
import com.air.mcp.gateway.model.identity.AuthMcpCommonInfo;
import com.air.mcp.gateway.resolver.McpAuthorityResolver;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.customizer.McpStatelessSyncServerCustomizer;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;
import java.util.Set;

/**
 * 这里spring ai 框架只提供了对tool类型的隐藏和过滤行为，根本原因如下：
 * MCP协议规范为三种原语定义了明确的控制主体，这决定了它们的风险等级完全不同：
 * Tools	模型 (Model)	LLM自主决定是否以及何时调用	高风险。模型可自主发起行动，可能产生副作用（如发邮件、删数据），必须严格管控其可见范围。
 * Resources	应用 (Application)	宿主应用决定将哪些资源作为上下文注入给模型	低风险。只读数据，模型无法主动“拉取”，由应用层控制，暴露风险可控。
 * Prompts	用户 (User)	人类用户主动从菜单中选择并触发	低风险。由人控制，是面向用户的快捷方式，而非模型的自主行为。
 */
@Slf4j
@Configuration
public class McpGatewayConfig {

    private static final String CLIENT_TYPE_NAME_KEY = "clientType";
    private static final String REQUEST_CLIENT_TYPE_NAME_KEY = "The-Client-Type";
    //有状态配置-sessionId空闲超时时间
    private static final Duration streamableSessionIdleTimeout = Duration.ofSeconds(3600);

    @Resource
    private PermissionSearchEngine permissionSearchEngine;
    @Resource
    private JacksonMcpJsonMapper jacksonMcpJsonMapper;

    // ================= Stateless模式-配置 =================

    /**
     * 无状态配置
     * 自定义endpoint端点
     * 传输层拦截定制化处理逻辑: 将request-header中的权限信息传递给McpTransportContext上下文中
     */
    @Bean
    @Profile("stateless")
    public WebMvcStatelessServerTransport statelessTransport(
            @Value("${spring.ai.mcp.server.stateless.mcp-endpoint}") String statelessHttpEndPointUrl
    ) {
        return WebMvcStatelessServerTransport.builder()
                //自定义端点路径
                .messageEndpoint(statelessHttpEndPointUrl)
                //自定义序列化器
                .jsonMapper(jacksonMcpJsonMapper)
                //自定义上下文提取逻辑
                .contextExtractor(serverRequest -> resetMcpTransportContext(serverRequest))
                .build();
    }

    @Bean
    @Profile("stateless")
    public RouterFunction<ServerResponse> statelessRouterFunction(WebMvcStatelessServerTransport statelessTransport) {
        return statelessTransport.getRouterFunction();
    }

    @Primary
    @Bean
    @Profile("stateless")
    public McpStatelessSyncServerCustomizer statelessToolVisibilityCustomizer(McpAuthorityResolver mcpAuthorityResolver) {
        return builder -> builder
                .addToolFilter((mcpTransportContext, tool) ->
                        checkToolVisibility(mcpTransportContext, tool, mcpAuthorityResolver));
    }

    // ================= Streamable模式-配置 =================

    /**
     * 有状态状态配置
     * 定义endpoint端点=streamableHttpEndPointUrl
     * 传输层拦截定制化处理逻辑: 将request-header中的权限信息传递给McpTransportContext上下文中
     */
    @Bean
    @Profile("streamable")
    public WebMvcStreamableServerTransportProvider streamableTransportProvider(
            @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint}") String streamableHttpEndPointUrl,
            @Value("${spring.ai.mcp.server.streamable-http.keep-alive-interval}") Duration streamableHttpKeepAliveInterval
    ) {
        return WebMvcStreamableServerTransportProvider.builder()
                //自定义端点路径
                .mcpEndpoint(streamableHttpEndPointUrl)
                //自定义序列化器
                .jsonMapper(jacksonMcpJsonMapper)
                .keepAliveInterval(streamableHttpKeepAliveInterval)
                .sessionIdleTimeout(this.streamableSessionIdleTimeout)
                //自定义上下文提取逻辑
                .contextExtractor(serverRequest -> resetMcpTransportContext(serverRequest))
                .build();
    }

    @Bean
    @Profile("streamable")
    public RouterFunction<ServerResponse> streamableRouterFunction(WebMvcStreamableServerTransportProvider streamableTransportProvider) {
        return streamableTransportProvider.getRouterFunction();
    }

    /**
     * 有状态
     * 按权限视角，返回客户端可见tools列表
     * Spring AI 2.0.2（底层MCP Java SDK 2.1.0）中，只有 tools/list 提供了过滤能力，而 Resource 和 Prompt 没有
     */
    @Primary
    @Bean
    @Profile("streamable")
    public McpSyncServerCustomizer streamableToolVisibilityCustomizer(McpAuthorityResolver mcpAuthorityResolver) {
        return builder -> builder
                .addToolFilter((mcpTransportContext, tool) ->
                        checkToolVisibility(mcpTransportContext, tool, mcpAuthorityResolver));
    }

    // ================= 公共提取器 =================

    private McpTransportContext resetMcpTransportContext(ServerRequest serverRequest) {
        HttpServletRequest request = serverRequest.servletRequest();
        String clientType = request.getHeader(REQUEST_CLIENT_TYPE_NAME_KEY);
        if (StringUtils.isBlank(clientType)) {
            log.error("【⭐】客户端未提供clientType,服务端无法判断连接的客户端类型，拒绝连接！！！，当前clientType={}", clientType);
            throw new RuntimeException("缺少 " + REQUEST_CLIENT_TYPE_NAME_KEY + " 请求头");
        }
        McpSupportClientTypeEnums clientTypeKeyEnum = McpSupportClientTypeEnums.getByTypeKey(clientType);
        if (ObjectUtils.isEmpty(clientTypeKeyEnum)) {
            log.error("【⭐】不支持的客户端类型:{},客户端类型仅仅支持【dify_agent_client_type,codex_agent_client_type】两种类型。当前连接拒绝！", clientType);
            throw new RuntimeException("不支持的客户端类型: " + clientType);
        }
        AbstractClientAuthHandler authHandler = permissionSearchEngine.findAuthHandlerByClientKeyEnum(clientTypeKeyEnum);
        if (ObjectUtils.isEmpty(authHandler)) {
            log.error("【⭐】无法找到客户端类型对应的handler当前连接拒绝！");
            throw new RuntimeException("未配置对应的 Handler");
        }
        return authHandler.extractAuthElementToMcpContext(request);
    }

    private boolean checkToolVisibility(McpTransportContext mcpTransportContext,
                                        McpSchema.Tool tool,
                                        McpAuthorityResolver mcpAuthorityResolver) {
        Object clientTypeObject = mcpTransportContext.get(CLIENT_TYPE_NAME_KEY);
        if (ObjectUtils.isEmpty(clientTypeObject)) {
            log.error("【⭐】上下文缺失 clientType，拒绝暴露工具");
            return false; // Filter 层遇到异常直接 return false
        }

        String clientType = (String) clientTypeObject;
        McpSupportClientTypeEnums clientTypeEnum = McpSupportClientTypeEnums.getByTypeKey(clientType);
        if (ObjectUtils.isEmpty(clientTypeEnum)) {
            log.error("【⭐】不支持的客户端类型: {}", clientType);
            return false;
        }

        AbstractClientAuthHandler handler = permissionSearchEngine.findAuthHandlerByClientKeyEnum(clientTypeEnum);
        if (ObjectUtils.isEmpty(handler)) {
            log.error("【⭐】无法找到客户端类型对应的 handler");
            return false;
        }

        AuthMcpCommonInfo authMcpCommonInfo = handler.buildMcpContextAuthObject(mcpTransportContext);
        Set<String> authElementCodeSet = mcpAuthorityResolver.resolveAuthElements(authMcpCommonInfo, McpSupportShareTypeEnums.MCP_TOOLS_TYPE);

        return authElementCodeSet != null && authElementCodeSet.contains(tool.name());
    }


}
