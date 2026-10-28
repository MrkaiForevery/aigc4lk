package com.air.mcp.gateway.service.prompt.code;

import com.air.mcp.gateway.annotation.CustomerMcpPromptMeta;
import com.air.mcp.gateway.annotation.RequireMcpPromptPermission;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeCheckPrompts {

    /**
     * prompt方法标定的返回的类型必须是：GetPromptResult
     */
    @McpPrompt(name = "code_review", description = "生成查询用户基本信息提示词模板")
    @CustomerMcpPromptMeta(domainId = "code",bizDomainId = "code_review_prompt001", sensitivity = "read") // 假设有这个注解
    @RequireMcpPromptPermission
    public McpSchema.GetPromptResult codeReviewPrompt(
            @McpArg(name = "language", description = "编程语言", required = true) String language,
            @McpArg(name = "code", description = "待审查的代码", required = true) String code,
            McpTransportContext ctx) {

        // 1. AOP 会在这里拦截并校验 ctx 中的身份是否允许访问 "code_review" prompt
        // 2. 组装 Prompt 内容
        String promptText = String.format("请对以下 %s 代码进行审查，指出潜在问题并给出改进建议：\n```%s\n%s\n```", language, language, code);

        // 3. 返回 GetPromptResult
        return new McpSchema.GetPromptResult(
                "Code Review Prompt",
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(promptText)))
        );
    }

}
