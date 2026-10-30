package com.air.mcp.gateway.model.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户端权限key绑定角色策略表
 * @date : 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "client_key_bind_role_policy", schema = "public")
public class ClientKeyBindRolePolicy implements Serializable, Cloneable {

    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 客户端类型(如dify/codex/....),; */
    private String clientType ;

    /** 客户端映射key(jsonb类),; */
    private PGobject clientMappingKeyJson ;

    /** 绑定的角色编码,; */
    private String bindRoleCode ;

    /** 策略描述,; */
    private String description ;

    /** 逻辑删除标识,; */
    private Boolean delLogic ;

    /** 创建者,; */
    private String createdBy ;

    /** 创建时间,; */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime ;

    /** 修改者,; */
    private String updateBy ;

    /** 修改时间,; */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime ;

}
