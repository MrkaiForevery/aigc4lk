package com.air.mcp.gateway.annotation;


import java.lang.annotation.*;

/**
 * 自定义mcpResources元数据标注注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomerMcpResourceMeta {
    /**资源所属域Id--如 monitor_950925*/
    String domainId();

    /**资源所属业务域Id--如payment_13579_monitor_950925*/
    String bizDomainId();

    /**资源可操作类型--如：read,write,execute*/
    String sensitivity();
}
