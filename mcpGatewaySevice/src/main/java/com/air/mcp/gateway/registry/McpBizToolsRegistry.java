package com.air.mcp.gateway.registry;

import com.air.mcp.gateway.annotation.meta.CustomerMcpToolMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将所有@McpTool通过执行BeanPostProcessor后置方式更新注册置IOC中,
 * 这是为了在启动过程,保证所有的McpTool在注册过程中能把@CustomerMcpToolMeta
 * 这种类似于用户自定义的注解里面的元数据暴露出来，形成一个句柄给
 * 类似于McpGatewayConfig中addToolsFilter()里面拿取用户自定义注解元素数据。
 */
@Component
public class McpBizToolsRegistry implements BeanPostProcessor {

    public final Map<String, BizToolMetadata> bizToolRegistryMap = new ConcurrentHashMap<>();

    public record BizToolMetadata(String name, String domainCode, String elementCode, String sensitivityType) {
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
                bizToolRegistryMap.put(meta.elementCode(), new BizToolMetadata(mcpTool.name(), meta.domainCode(), meta.elementCode(), meta.sensitivityType()));
            }
        });
        return bean;
    }

    public Map<String, BizToolMetadata> getAllBizToolMetadataMap() {
        return this.bizToolRegistryMap;
    }
}
