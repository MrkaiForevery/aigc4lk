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
@TableName(value = "agent_user_role_mapping", schema = "permission_domain_namespace")
public class AgentUserRoleMapping implements Serializable, Cloneable {

    /**
     * 主键id,;
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 代理 ID (varchar(128), NOT NULL)
     */
    private String agentId;

    /**
     * 用户角色 (varchar(128), NOT NULL)
     */
    private String userRole;

    /**
     * 策略角色 ID (varchar(128), NOT NULL)
     */
    private String policyRoleId;

    /**
     * 用户角色 ID (varchar(128), 可为空)
     */
    private String userRoleId;

    /**
     * mcp的类型，参考枚举McpShareTypeEnums
     */
    private String mcpType;
}
