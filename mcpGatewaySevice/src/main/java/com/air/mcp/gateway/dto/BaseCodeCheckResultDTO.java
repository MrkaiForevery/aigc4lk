package com.air.mcp.gateway.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 测试用DTO对象
 * @date : 2026-11-4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseCodeCheckResultDTO implements Serializable, Cloneable {

    @JsonPropertyDescription("用户唯一标识ID")
    private String userId;

    @JsonPropertyDescription("用户真实姓名")
    private String name;

    @JsonPropertyDescription("用户年龄，整数类型")
    private Integer age;

    @JsonPropertyDescription("系统唯一标识")
    private String systemId;

    @JsonPropertyDescription("系统中文名称")
    private String system;

    @JsonPropertyDescription("系统的描述信息")
    private String description;
}
