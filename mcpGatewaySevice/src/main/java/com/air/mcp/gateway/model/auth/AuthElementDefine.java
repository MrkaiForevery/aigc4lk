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
@TableName(value = "auth_element_def", schema = "public")
public class AuthElementDefine implements Serializable, Cloneable {

    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 元素类型(tool/resource/prompt),; */
    private String elementType ;

    /** 元素敏感度类型(read/write/...),; */
    private String sensitivityType ;

    /** 元素编码,; */
    private String elementCode ;

    /** 元素名称,; */
    private String elementName ;

    /** 元素所在域编码,; */
    private String relationDomainCode ;

    /** 元素描述,; */
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
