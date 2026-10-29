package com.air.mcp.gateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireMcpResourcePermission {

    //资源编码
    String resourceCode() ;

    //资源名
    String resourceName() default "";
}
