package com.air.mcp.gateway.aspect;

import com.air.mcp.gateway.annotation.permission.RequireMcpPromptPermission;
import com.air.mcp.gateway.annotation.permission.RequireMcpResourcePermission;
import com.air.mcp.gateway.annotation.permission.RequireMcpToolPermission;
import com.air.mcp.gateway.auth.PermissionSearchEngine;
import com.air.mcp.gateway.auth.handler.AbstractClientAuthHandler;
import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.enums.McpSupportShareTypeEnums;
import com.air.mcp.gateway.model.identity.AuthMcpCommonInfo;
import com.air.mcp.gateway.resolver.McpAuthorityResolver;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * McpTransportContext：轻量级“传输层上下文”
 * 它的定位是无状态操作，提供对传输层（如 HTTP 请求头、会话信息）的轻量级访问，不涉及完整的服务器交换（Server Exchange）功能。它适合需要获取原始请求信息（比如你用来提取自定义 Header X-Agent-ID）的场景。
 * <p>
 * McpMeta：结构化的“元数据访问器”
 * 它的定位是访问 MCP 协议中 _meta 字段的元数据，这些元数据可以来自请求、通知或结果。它通过 get(String key) 方法提供便捷的键值访问，更像一个标准化的元数据包。
 */

/**
 * mcp的tools & resource & prompt 被调用时的权限验证切面
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class McpAuthorizationAspect {

    private final McpAuthorityResolver mcpAuthorityResolver;
    private final PermissionSearchEngine permissionSearchEngine;

    // ======================== Tool 权限拦截 ========================
    @Before("@annotation(requireMcpToolPermission) && @annotation(org.springframework.ai.mcp.annotation.McpTool)")
    public void checkMcpToolsPermission(JoinPoint joinPoint, RequireMcpToolPermission requireMcpToolPermission) {
        // 1. 获取当前被拦截的tool方法
        String toolElementCode = getToolElementCode(joinPoint, requireMcpToolPermission);

        // 2. 从方法参数中提取 McpTransportContext
        McpTransportContext ctx = extractTransportContext(joinPoint.getArgs());
        if (ctx == null) {
            throw McpError.builder(500).message("INVALID_CONTEXT:无法获取 MCP 传输上下文").build();
        }

        // 获取指定的handler
        String clientType = (String) ctx.get("clientType");
        McpSupportClientTypeEnums clientTypeEnum = McpSupportClientTypeEnums.getByTypeKey(clientType);
        AbstractClientAuthHandler clientAuthHandler = permissionSearchEngine.findAuthHandlerByClientKeyEnum(clientTypeEnum);

        if (clientAuthHandler == null) {
            return;
        }

        //使用handler构建权限通用对象
        AuthMcpCommonInfo authCommonInfo = clientAuthHandler.buildMcpContextAuthObject(ctx);

        // 4. 校验权限(调用专门针对 Tool 的解析方法)
        Set<String> visibleToolsCode = mcpAuthorityResolver.resolveAuthElements(authCommonInfo, McpSupportShareTypeEnums.MCP_TOOLS_TYPE);
        if (!visibleToolsCode.contains(toolElementCode)) {
            log.warn("越权调用(tool)拦截: toolElementCode,authCommonInfo={},visibleToolsCode={}",toolElementCode ,authCommonInfo,visibleToolsCode);
            throw McpError.builder(502).message("TOOL_FORBIDDEN:无权调用该工具").build();
        }
    }

    private static String getToolElementCode(JoinPoint joinPoint, RequireMcpToolPermission requireMcpToolPermission) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 这里以工具编码为唯一标识
        String toolCode = requireMcpToolPermission.toolCode();
        if (toolCode.isEmpty()) {
            McpTool mcpTool = method.getAnnotation(McpTool.class);
            if (mcpTool != null) {
                toolCode = mcpTool.name();
            } else {
                throw new IllegalStateException("方法既没有指定toolCode，也没有 @McpTool 注解");
            }
        }
        return toolCode;
    }


    // ======================== Resource 权限拦截 ========================
    @Before("@annotation(requireMcpResourcePermission) && @annotation(org.springframework.ai.mcp.annotation.McpResource)")
    public void checkMcpResourcePermission(JoinPoint joinPoint, RequireMcpResourcePermission requireMcpResourcePermission) {
        // 1. 获取当前被拦截的 ResourceCode 标识
        String resourceElementCode = getResourceElementKey(joinPoint, requireMcpResourcePermission);

        // 提取身份信息
        McpTransportContext ctx = extractTransportContext(joinPoint.getArgs());
        if (ctx == null) {
            throw McpError.builder(500).message("INVALID_CONTEXT:无法获取 MCP 传输上下文").build();
        }

        // 获取指定的handler
        String clientType = (String) ctx.get("clientType");
        McpSupportClientTypeEnums clientTypeEnum = McpSupportClientTypeEnums.getByTypeKey(clientType);
        AbstractClientAuthHandler clientAuthHandler = permissionSearchEngine.findAuthHandlerByClientKeyEnum(clientTypeEnum);

        if (clientAuthHandler == null) {
            return;
        }

        //使用handler构建权限通用对象
        AuthMcpCommonInfo authCommonInfo = clientAuthHandler.buildMcpContextAuthObject(ctx);

        // 4. 校验权限 (调用专门针对 Resource 的解析方法)
        Set<String> visibleResourcesCode = mcpAuthorityResolver.resolveAuthElements(authCommonInfo,McpSupportShareTypeEnums.MCP_RESOURCES_TYPE);
        if (!visibleResourcesCode.contains(resourceElementCode)) {
            log.warn("越权访问拦截(Resource):resourceElementCode={}, authCommonInfo={}, visibleResourcesCode={}",resourceElementCode,authCommonInfo,visibleResourcesCode);
            throw McpError.builder(403).message("RESOURCE_FORBIDDEN:无权访问该资源").build();
        }
    }

    private String getResourceElementKey(JoinPoint joinPoint, RequireMcpResourcePermission perm) {

        //如果能从RequireMcpResourcePermission能获取到resourceElementCode
        if (StringUtils.hasText(perm.resourceCode())) {
            return perm.resourceCode();
        }
        //如果不能就直接从McpResource的name中拿
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        McpResource resource = method.getAnnotation(McpResource.class);

        return resource.name();
    }

    // ======================== Prompt 权限拦截 ========================
    @Before("@annotation(requireMcpPromptPermission) && @annotation(org.springframework.ai.mcp.annotation.McpPrompt)")
    public void checkMcpPromptPermission(JoinPoint joinPoint, RequireMcpPromptPermission requireMcpPromptPermission) {
        // 1. 获取当前被拦截的 Prompt 标识
        String promptElementCode = getPromptElementCode(joinPoint, requireMcpPromptPermission);

        // 2. 提取身份信息
        McpTransportContext ctx = extractTransportContext(joinPoint.getArgs());
        if (ctx == null) {
            throw McpError.builder(500).message("INVALID_CONTEXT:无法获取 MCP 传输上下文").build();
        }

        // 获取指定的handler
        String clientType = (String) ctx.get("clientType");
        McpSupportClientTypeEnums clientTypeEnum = McpSupportClientTypeEnums.getByTypeKey(clientType);
        AbstractClientAuthHandler clientAuthHandler = permissionSearchEngine.findAuthHandlerByClientKeyEnum(clientTypeEnum);

        if (clientAuthHandler == null) {
            return;
        }

        //使用handler构建权限通用对象
        AuthMcpCommonInfo authCommonInfo = clientAuthHandler.buildMcpContextAuthObject(ctx);

        // 校验权限 (调用专门针对 Prompt 的解析方法)
        Set<String> visiblePromptElementsCode = mcpAuthorityResolver.resolveAuthElements(authCommonInfo,McpSupportShareTypeEnums.MCP_PROMPT_TYPE);
        if (!visiblePromptElementsCode.contains(promptElementCode)) {
            log.warn("越权访问拦截(Prompt):promptElementCode={} ,authCommonInfo={}, visiblePromptElementsCode={}",promptElementCode,authCommonInfo,visiblePromptElementsCode);
            throw McpError.builder(403).message("PROMPT_FORBIDDEN:无权访问该提示词").build();
        }
    }

    private String getPromptElementCode(JoinPoint joinPoint, RequireMcpPromptPermission perm) {
        if (StringUtils.hasText(perm.promptCode())) {
            return perm.promptCode();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        McpPrompt prompt = method.getAnnotation(McpPrompt.class);

        if (prompt != null) {
            return prompt.name();
        }
        throw new IllegalStateException("方法既没有指定 value，也没有 @McpPrompt 注解");
    }


    // 从参数数组中找出 McpTransportContext
    private McpTransportContext extractTransportContext(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof McpTransportContext) {
                return (McpTransportContext) arg;
            }
        }
        return null;
    }
}
