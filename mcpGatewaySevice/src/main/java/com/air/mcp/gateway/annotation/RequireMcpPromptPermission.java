package com.air.mcp.gateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireMcpPromptPermission {

    //提示词编码
    String promptCode() ;

    //提示词名
    String promptName() default "";
}
