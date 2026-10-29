package com.air.mcp.gateway.model.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限域定义表(树型结构)数据表
 * @date : 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "auth_domain_def", schema = "public")
public class AuthDomainDefine implements Serializable, Cloneable {

    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 域级别,; */
    private byte domainLevel ;

    /** 父域编码,; */
    private String fatherDomainCode ;

    /** 域编码,; */
    private String domainCode ;

    /** 域名称,; */
    private String domainName ;

    /** 域描述,; */
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
