package com.air.mcp.gateway.auth.handler;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.model.auth.AuthBlackListMapping;
import org.postgresql.util.PGobject;

import java.util.List;
import java.util.Map;
import java.util.Set;


public abstract class AbstractBlackListParserHandler {

    public abstract Map<String,List<String>> parserBlackList(List<AuthBlackListMapping> baseBlackList);

    public abstract McpSupportClientTypeEnums getHandleClientType();

    public abstract Set<String> doFilter(List<String> userIdJsonList, Map<String, List<String>> allBaseBlackMap, Set<String> baseElementCodeSet);
}
