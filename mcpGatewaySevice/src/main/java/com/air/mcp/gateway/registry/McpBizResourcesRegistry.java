package com.air.mcp.gateway.registry;

import com.air.mcp.gateway.annotation.meta.CustomerMcpResourceMeta;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将所有@McpResource通过执行BeanPostProcessor后置方式更新注册置IOC中,
 * 这是为了在启动过程,保证所有的McpResource在注册过程中能把@CustomerMcpResourceMeta
 * 这种类似于用户自定义的注解里面的元数据暴露出来。暂时没有作用，可以保留
 */
@Component
public class McpBizResourcesRegistry implements BeanPostProcessor {

    public final Map<String, BizResourceMetadata> bizResourceRegistryMap = new ConcurrentHashMap<>();

    public record BizResourceMetadata(String name, String domainCode, String elementCode, String sensitivityType) {
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
                bizResourceRegistryMap.put(meta.elementCode(), new BizResourceMetadata(mcpResource.name(), meta.domainCode(), meta.elementCode(), meta.sensitivityType()));
            }
        });
        return bean;
    }

    public Map<String, BizResourceMetadata> getAllBizResourcesMetadataMap() {
        return this.bizResourceRegistryMap;
    }
}
