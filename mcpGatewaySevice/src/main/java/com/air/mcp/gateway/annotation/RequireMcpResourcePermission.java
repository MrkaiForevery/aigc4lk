package com.air.mcp.gateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireMcpResourcePermission {

    // 如果留空，则默认获取 @McpResource 的 name 属性
    String value() default "";
}
