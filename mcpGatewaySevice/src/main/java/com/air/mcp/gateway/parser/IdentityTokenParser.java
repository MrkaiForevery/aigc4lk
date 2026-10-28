package com.air.mcp.gateway.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class IdentityTokenParser {

    public Set<String> parseUserTokenRoles(String token){
        //todo 这里先模拟
        return Set.of("sys_admin_role","identity_admin_role","order_admin_role","order_jd_role","code_admin");
    }

    public String parseUserId(String token){
        //todo 这里先模拟
        return "sys_00001";
    }
}
