package com.air.mcp.gateway.annotation;

import java.lang.annotation.*;

/**
 * 自定义mcpTools元数据标注注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomerMcpToolMeta {

    /**工具元素所属域code编码*/
    String domainCode();

    /**工具元素可操作类型--如：read,write,execute*/
    String sensitivityType();

    /**工具元素自身code编码*/
    String elementCode();
}
