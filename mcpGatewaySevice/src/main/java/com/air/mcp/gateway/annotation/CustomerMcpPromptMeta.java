package com.air.mcp.gateway.annotation;


import java.lang.annotation.*;

/**
 * 自定义mcpPrompts元数据标注注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomerMcpPromptMeta {
    /**提示词元素所属域编码*/
    String domainCode();

    /**提示词元素可操作类型--如：read,write,execute*/
    String sensitivityType();

    /**提示词元素自身code编码*/
    String elementCode();
}
