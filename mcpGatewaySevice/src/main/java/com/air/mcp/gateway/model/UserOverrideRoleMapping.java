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
@TableName(value = "user_override_role_mapping", schema = "permission_domain_namespace")
public class UserOverrideRoleMapping implements Serializable,Cloneable{

    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 代理 ID (varchar(64), NOT NULL)
     */
    private String agentId;

    /**
     * 用户 ID (varchar(64), NOT NULL)
     */
    private String userId;

    /**
     * 工具名称 (varchar(128), NOT NULL)
     */
    private String toolName;

    /**
     * 效果 (varchar(8), NOT NULL)
     */
    private String effect;

    /**
     * 域 ID (varchar(64), 可为空)
     */
    private String domainId;

    /**
     * 业务域 ID (varchar(64), 可为空)
     */
    private String bizDomainId;

    /**
     * mcp的类型，参考枚举McpShareTypeEnums
     */
    private String mcpType;
}
