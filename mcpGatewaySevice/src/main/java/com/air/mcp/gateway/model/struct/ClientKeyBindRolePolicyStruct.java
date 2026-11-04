package com.air.mcp.gateway.model.struct;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客户端权限key绑定角色策略BO对象
 * @date : 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientKeyBindRolePolicyStruct {

    /** 主键id,; */
    private Integer id;

    /** 客户端类型(如dify/codex/....),; */
    private String clientType ;

    /** 客户端映射key(结构类),; */
    private ClientKeyMappingStruct clientKeyMappingStruct ;

    /** 绑定的角色编码,; */
    private String bindRoleCode ;

    /** 策略描述,; */
    private String description ;

    /** 逻辑删除标识,; */
    private Boolean delLogic ;

    /** 创建者,; */
    private String createdBy ;

    /** 创建时间,; */
    private LocalDateTime createdTime ;

    /** 修改者,; */
    private String updateBy ;

    /** 修改时间,; */
    private LocalDateTime updateTime ;

}
