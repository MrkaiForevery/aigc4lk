package com.air.mcp.gateway.registry;

import com.air.mcp.gateway.annotation.CustomerMcpToolMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将所有McpTools通过执行BeanPostProcessor后置方式更新注册置IOC中,
 * 保证注入顺序,方便后续从IOC中取
 */

@Component
public class McpBizToolsRegistry implements BeanPostProcessor {

    public final Map<String, BizToolMetadata> bizToolRegistryMap = new ConcurrentHashMap<>();

    public record BizToolMetadata(String name, String domainId, String bizDomainId, String sensitivity) {
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(targetClass, method -> {
            McpTool mcpTool = method.getAnnotation(McpTool.class);
            if (mcpTool != null) {
                CustomerMcpToolMeta meta = method.getAnnotation(CustomerMcpToolMeta.class);
                if (meta == null) {
                    throw new IllegalStateException("启动失败：工具 " + mcpTool.name() + " 缺失 @McpToolMeta 标签！");
                }
                bizToolRegistryMap.put(mcpTool.name(), new BizToolMetadata(mcpTool.name(), meta.domainId(), meta.bizDomainId(), meta.sensitivity()));
            }
        });
        return bean;
    }

    public Map<String, BizToolMetadata> getAllBizToolMetadataMap() {
        return this.bizToolRegistryMap;
    }
}
