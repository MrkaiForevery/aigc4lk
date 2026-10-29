package com.air.mcp.gateway.registry;

import com.air.mcp.gateway.annotation.CustomerMcpPromptMeta;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将所有McpPrompts通过执行BeanPostProcessor后置方式更新注册置IOC中,
 * 保证注入顺序,方便后续从IOC中取
 */

@Component
public class McpBizPromptsRegistry implements BeanPostProcessor {

    public final Map<String, BizPromptMetadata> bizPromptRegistryMap = new ConcurrentHashMap<>();

    public record BizPromptMetadata(String name, String domainCode, String elementCode, String sensitivityType) {
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(targetClass, method -> {
            McpPrompt mcpPrompt = method.getAnnotation(McpPrompt.class);
            if (mcpPrompt != null) {
                CustomerMcpPromptMeta meta = method.getAnnotation(CustomerMcpPromptMeta.class);
                if (meta == null) {
                    throw new IllegalStateException("启动失败：提示词 " + mcpPrompt.name() + " 缺失 @McpPromptMeta 标签！");
                }
                bizPromptRegistryMap.put(meta.elementCode(), new BizPromptMetadata(mcpPrompt.name(), meta.domainCode(), meta.elementCode(), meta.sensitivityType()));
            }
        });
        return bean;
    }

    public Map<String, BizPromptMetadata> getAllBizPromptsMetadataMap() {
        return this.bizPromptRegistryMap;
    }
}
