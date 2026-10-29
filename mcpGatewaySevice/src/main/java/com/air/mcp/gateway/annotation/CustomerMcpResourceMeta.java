package com.air.mcp.gateway.annotation;


import java.lang.annotation.*;

/**
 * 自定义mcpResources元数据标注注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomerMcpResourceMeta {

    /**资源元素所属域编码*/
    String domainCode();

    /**资源元素可操作类型--如：read,write,execute*/
    String sensitivityType();

    /**工资源元素自身code编码*/
    String elementCode();
}
