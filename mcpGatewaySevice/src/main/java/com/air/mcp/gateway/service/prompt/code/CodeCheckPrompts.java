package com.air.mcp.gateway.service.prompt.code;

import com.air.mcp.gateway.annotation.meta.CustomerMcpPromptMeta;
import com.air.mcp.gateway.annotation.permission.RequireMcpPromptPermission;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @McpPrompt 的设计初衷就是：由服务端接收用户的变量输入，在代码里把“固定话术”和“变量”组装成一段最终的、完美的文本，然后交给客户端（如 Dify）发给大模型。
 */
@Service
@RequiredArgsConstructor
public class CodeCheckPrompts {

    /**
     * prompt方法标定的返回的类型必须是：GetPromptResult，且@McpPrompt中的name必须全局唯一
     */
    @McpPrompt(name = "fill_code_check_prompt", description = "填充代码审查的提示词内容")
    @CustomerMcpPromptMeta(domainCode = "211100000", sensitivityType = "read", elementCode = "fill_code_check_prompt")
    @RequireMcpPromptPermission(promptCode = "fill_code_check_prompt", promptName = "fillCodeCheckPrompt")
    public McpSchema.GetPromptResult fillCodeCheckPrompt(
            @McpArg(name = "language", description = "编程语言，如 Java, Python", required = true) String language,
            @McpArg(name = "code", description = "待审查的代码内容", required = true) String code,
            McpTransportContext ctx) {
        String promptText = getPromptText(language, code);
        // 返回 GetPromptResult
        return new McpSchema.GetPromptResult(
                "Code Review Prompt",
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(promptText)))
        );
    }

    private static @NonNull String getPromptText(String language, String code) {
        // 组装 Prompt 内容
        String templateStr = """
                            请作为一名资深开发工程师，对以下 {language} 代码进行 Code Review。
                            指出潜在 Bug、性能问题，并给出改进建议。
                
                            代码如下：
                            ```{language}
                            {code}
                """;

        String promptText = PromptTemplate.builder()
                .template(templateStr)
                .variables(Map.of("language", language, "code", code))
                .build()
                .create()
                .getContents();
        return promptText;
    }


}
