package com.air.mcp.gateway.auth;

import cn.hutool.extra.spring.SpringUtil;
import com.air.mcp.gateway.auth.handler.AbstractBlackListParserHandler;
import com.air.mcp.gateway.auth.handler.AbstractClientAuthHandler;
import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import feign.Client;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.map.CompositeMap;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限查找引擎
 */
@Component
@RequiredArgsConstructor
public class PermissionSearchEngine {

    private static final Map<McpSupportClientTypeEnums, AbstractClientAuthHandler> clientAuthHandlerMap = new ConcurrentHashMap<>();
    private static final Map<McpSupportClientTypeEnums, AbstractBlackListParserHandler> clientBlackListMap = new ConcurrentHashMap<>();


    private final ApplicationContext applicationContext;

    @PostConstruct
    public void initHandlerMap(){
        Map<String, AbstractClientAuthHandler> nameOfMap = applicationContext.getBeansOfType(AbstractClientAuthHandler.class);
        nameOfMap.forEach((name,handler) -> {
            McpSupportClientTypeEnums handleClientType = handler.getHandleClientType();
            clientAuthHandlerMap.put(handleClientType,handler);
        });

        Map<String, AbstractBlackListParserHandler> beansOfType = applicationContext.getBeansOfType(AbstractBlackListParserHandler.class);
        beansOfType.forEach((name,handler) -> {
            McpSupportClientTypeEnums handleClientType = handler.getHandleClientType();
            clientBlackListMap.put(handleClientType,handler);
        });
    }

    public AbstractClientAuthHandler findAuthHandlerByClientKeyEnum(McpSupportClientTypeEnums clientTypeEnum){
        return this.clientAuthHandlerMap.get(clientTypeEnum);
    }

    public AbstractBlackListParserHandler findBlackListHandlerByClientKeyEnum(McpSupportClientTypeEnums clientTypeEnum){
        return this.clientBlackListMap.get(clientTypeEnum);
    }

}
