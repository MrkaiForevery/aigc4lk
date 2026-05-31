package com.air.document.mcp;

import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DynamicMcpToolProvider implements ToolCallbackProvider {
    @Resource
    private ApplicationContext context;

    @Override
    public ToolCallback[] getToolCallbacks() {
        // 动态查找所有 ToolCallbackProvider，排除自身
        Map<String, ToolCallbackProvider> providers = context.getBeansOfType(ToolCallbackProvider.class);
        for (ToolCallbackProvider provider : providers.values()) {
            if (!(provider instanceof DynamicMcpToolProvider)) {
                return provider.getToolCallbacks();
            }
        }
        // 如果没找到任何外部提供者，返回空数组，保证应用正常启动
        return new ToolCallback[0];
    }
}