package com.air.mcp.gateway.registry;

import com.air.mcp.gateway.annotation.CustomerMcpResourceMeta;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将所有McpResources通过执行BeanPostProcessor后置方式更新注册置IOC中,
 * 保证注入顺序,方便后续从IOC中取
 */

@Component
public class McpBizResourcesRegistry implements BeanPostProcessor {

    public final Map<String, BizResourceMetadata> bizResourceRegistryMap = new ConcurrentHashMap<>();

    public record BizResourceMetadata(String name, String domainId, String bizDomainId, String sensitivity) {
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(targetClass, method -> {
            McpResource mcpResource = method.getAnnotation(McpResource.class);
            if (mcpResource != null) {
                CustomerMcpResourceMeta meta = method.getAnnotation(CustomerMcpResourceMeta.class);
                if (meta == null) {
                    throw new IllegalStateException("启动失败：资源 " + mcpResource.name() + " 缺失 @McpResourceMeta 标签！");
                }
                bizResourceRegistryMap.put(mcpResource.name(), new BizResourceMetadata(mcpResource.name(), meta.domainId(), meta.bizDomainId(), meta.sensitivity()));
            }
        });
        return bean;
    }

    public Map<String, BizResourceMetadata> getAllBizResourcesMetadataMap() {
        return this.bizResourceRegistryMap;
    }
}
