package com.air.mcp.gateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "role_policy_definition", schema = "permission_domain_namespace")
public class RolePolicyDefinition implements Serializable, Cloneable {

    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 策略角色 (varchar(64), NOT NULL)
     */
    private String policyRole;

    /**
     * 域 (varchar(258), NOT NULL)
     */
    private String domain;

    /**
     * 敏感级别 (varchar(32), NOT NULL)
     */
    private String sensitivity;

    /**
     * 效果 (varchar(32), NOT NULL)
     */
    private String effect;

    /**
     * 域 ID (varchar(64), NOT NULL)
     */
    private String domainId;

    /**
     * 业务域 (varchar(258), 可为空)
     */
    private String bizDomain;

    /**
     * 业务域 ID (varchar(64), 可为空)
     */
    private String bizDomainId;

    /**
     * mcp的类型，参考枚举McpShareTypeEnums
     */
    private String mcpType;
}
