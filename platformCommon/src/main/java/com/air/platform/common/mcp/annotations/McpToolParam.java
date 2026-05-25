package com.air.platform.common.mcp.annotations;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolParam {
    String description() default "";
    boolean required() default true;
}