package com.air.mcp.gateway.aspect;

import com.air.mcp.gateway.annotation.RequireMcpPromptPermission;
import com.air.mcp.gateway.annotation.RequireMcpResourcePermission;
import com.air.mcp.gateway.annotation.RequireMcpToolPermission;
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
 * mcp调用时的权限验证切面
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class McpAuthorizationAspect {

    private final McpAuthorityResolver mcpAuthorityResolver;

    // ======================== Tool 权限拦截 ========================
    @Before("@annotation(requireMcpToolPermission) && @annotation(org.springframework.ai.mcp.annotation.McpTool)")
    public void checkMcpToolsPermission(JoinPoint joinPoint, RequireMcpToolPermission requireMcpToolPermission) {
        // 1. 获取当前被拦截的tool方法
        String toolName = getToolName(joinPoint, requireMcpToolPermission);

        // 2. 从方法参数中提取 McpTransportContext
        McpTransportContext ctx = extractTransportContext(joinPoint.getArgs());
        if (ctx == null) {
            throw McpError.builder(500).message("INVALID_CONTEXT:无法获取 MCP 传输上下文").build();
        }

        // 3. 包装身份信息
        AuthMcpCommonInfo authMcpCommonInfo = buildIdentityMcpInfo(ctx);

        // 4. 校验权限(调用专门针对 Tool 的解析方法)
        Set<String> visible = mcpAuthorityResolver.resolveMcpBizTools(authMcpCommonInfo.getAgentId(), authMcpCommonInfo.getUserRoles(), authMcpCommonInfo.getUserId());
        if (!visible.contains(toolName)) {
            log.warn("越权调用拦截: agentId={}, userId={}, tool={}", authMcpCommonInfo.getAgentId(), authMcpCommonInfo.getUserId(), toolName);
            throw McpError.builder(502).message("TOOL_FORBIDDEN:无权调用该工具").build();
        }
    }

    private static String getToolName(JoinPoint joinPoint, RequireMcpToolPermission requireMcpToolPermission) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 确定工具名称（优先用注解的 value，否则取 @McpTool 的 name）
        String toolName = requireMcpToolPermission.value();
        if (toolName.isEmpty()) {
            McpTool mcpTool = method.getAnnotation(McpTool.class);
            if (mcpTool != null) {
                toolName = mcpTool.name();
            } else {
                throw new IllegalStateException("方法既没有指定 value，也没有 @McpTool 注解");
            }
        }
        return toolName;
    }


    // ======================== Resource 权限拦截 ========================
    @Before("@annotation(requireMcpResourcePermission) && @annotation(org.springframework.ai.mcp.annotation.McpResource)")
    public void checkMcpResourcePermission(JoinPoint joinPoint, RequireMcpResourcePermission requireMcpResourcePermission) {
        // 1. 获取当前被拦截的 Resource 标识 (uriTemplate 优先)
        String resourceKey = getResourceKey(joinPoint, requireMcpResourcePermission);

        // 2. 提取身份信息
        McpTransportContext ctx = extractTransportContext(joinPoint.getArgs());
        if (ctx == null) {
            throw McpError.builder(500).message("INVALID_CONTEXT:无法获取 MCP 传输上下文").build();
        }

        // 3. 包装身份信息
        AuthMcpCommonInfo authMcpCommonInfo = buildIdentityMcpInfo(ctx);

        // 4. 校验权限 (调用专门针对 Resource 的解析方法)
        Set<String> visible = mcpAuthorityResolver.resolveMcpBizResources(authMcpCommonInfo.getAgentId(), authMcpCommonInfo.getUserRoles(), authMcpCommonInfo.getUserId());
        if (!visible.contains(resourceKey)) {
            log.warn("越权访问拦截(Resource): agentId={}, userId={}, resource={}", authMcpCommonInfo.getAgentId(), authMcpCommonInfo.getUserId(), resourceKey);
            throw McpError.builder(403).message("RESOURCE_FORBIDDEN:无权访问该资源").build();
        }
    }

    private String getResourceKey(JoinPoint joinPoint, RequireMcpResourcePermission perm) {
        // 如果注解自定义了 value，优先使用
        if (StringUtils.hasText(perm.value())) {
            return perm.value();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        McpResource resource = method.getAnnotation(McpResource.class);

        // 使用固定 uri
        return resource.uri();
    }

    // ======================== Prompt 权限拦截 ========================
    @Before("@annotation(requireMcpPromptPermission) && @annotation(org.springframework.ai.mcp.annotation.McpPrompt)")
    public void checkMcpPromptPermission(JoinPoint joinPoint, RequireMcpPromptPermission requireMcpPromptPermission) {
        // 1. 获取当前被拦截的 Prompt 标识
        String promptName = getPromptName(joinPoint, requireMcpPromptPermission);

        // 2. 提取身份信息
        McpTransportContext ctx = extractTransportContext(joinPoint.getArgs());
        if (ctx == null) {
            throw McpError.builder(500).message("INVALID_CONTEXT:无法获取 MCP 传输上下文").build();
        }

        // 3. 包装身份信息
        AuthMcpCommonInfo authMcpCommonInfo = buildIdentityMcpInfo(ctx);

        // 4. 校验权限 (调用专门针对 Prompt 的解析方法)
        Set<String> visible = mcpAuthorityResolver.resolveMcpBizPrompts(authMcpCommonInfo.getAgentId(), authMcpCommonInfo.getUserRoles(), authMcpCommonInfo.getUserId());
        if (!visible.contains(promptName)) {
            log.warn("越权访问拦截(Prompt): agentId={}, userId={}, prompt={}", authMcpCommonInfo.getAgentId(), authMcpCommonInfo.getUserRoles(), promptName);
            throw McpError.builder(403).message("PROMPT_FORBIDDEN:无权访问该提示词").build();
        }
    }

    private String getPromptName(JoinPoint joinPoint, RequireMcpPromptPermission perm) {
        if (StringUtils.hasText(perm.value())) {
            return perm.value();
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


    private AuthMcpCommonInfo buildIdentityMcpInfo(McpTransportContext ctx) {
        String agentId = (String) ctx.get("agentId");
        String userId = (String) ctx.get("userId");
        Set<String> roles = (Set<String>) ctx.get("userRoles");
        return AuthMcpCommonInfo.builder()
                .agentId(agentId)
                .userId(userId)
                .userRoles(roles)
                .build();
    }
}
