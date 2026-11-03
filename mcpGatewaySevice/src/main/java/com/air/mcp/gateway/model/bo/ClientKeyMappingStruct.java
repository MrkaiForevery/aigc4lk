package com.air.mcp.gateway.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 客户端权限key结构对象
 * @date : 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientKeyMappingStruct implements Serializable, Cloneable {

    /** dify客户端映射key结构,; */
    private DifyClientKeyStruct difyClientKeyStruct;

    /** codex客户端key结构,; */
    private CodexClientKeyStruct codexClientKeyStruct;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DifyClientKeyStruct{
        /** 客户端智能体id; */
        private String agentId ;
        /** 客户端角色,; */
        private String userRole ;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodexClientKeyStruct{
        /** 客户端智能体id; */
        private String agentId ;
        /** 是否全局校验; */
        private Boolean globalAuth;
        /** 客户端角色; */
        private String operatorAuth;
    }
}
