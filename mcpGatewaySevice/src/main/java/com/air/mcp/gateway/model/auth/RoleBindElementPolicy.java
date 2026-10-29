package com.air.mcp.gateway.model.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限角色绑定权限元素策略表
 * @date : 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "role_bind_element_policy", schema = "public")
public class RoleBindElementPolicy implements Serializable, Cloneable {

    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 角色编号,; */
    private String roleCode ;

    /** 元素编码,; */
    private String elementCode ;

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
