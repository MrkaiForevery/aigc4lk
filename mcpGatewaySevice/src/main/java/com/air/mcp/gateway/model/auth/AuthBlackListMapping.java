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
 * 权限黑名单映射表
 * @date : 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "auth_black_list_mapping", schema = "public")
public class AuthBlackListMapping implements Serializable, Cloneable {

    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 客户端类型(如dify/codex/....),; */
    private String clientType ;

    /** 客户端映射key(json类),; */
    private String blackMappingKeyJson ;

    /** 拒绝使用的权限元素,; */
    private String denyTargetElementCodes ;

    /** 是否生效,; */
    private Boolean effectFlag ;

    /** 描述,; */
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
