package com.air.mcp.gateway.auth;

import cn.hutool.extra.spring.SpringUtil;
import com.air.mcp.gateway.auth.handler.AbstractBlackListParserHandler;
import com.air.mcp.gateway.auth.handler.AbstractClientAuthHandler;
import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import feign.Client;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.CompositeMap;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限查找引擎
 */
@Slf4j
@Component
public class PermissionSearchEngine {

    private  final Map<McpSupportClientTypeEnums, AbstractClientAuthHandler> clientAuthHandlerMap ;
    private  final Map<McpSupportClientTypeEnums, AbstractBlackListParserHandler> clientBlackListMap ;

    /**
     * 通过构造器直接注入 List，Spring 会保证所有实现类完全初始化后才注入
     */
    public PermissionSearchEngine(List<AbstractClientAuthHandler> authHandlers,
                                  List<AbstractBlackListParserHandler> blackListHandlers) {

        // 1. 将 AuthHandler List 转换为 Map
        this.clientAuthHandlerMap = safeToMap(authHandlers, AbstractClientAuthHandler::getHandleClientType);

        // 2. 将 BlackListHandler List 转换为 Map
        this.clientBlackListMap = safeToMap(blackListHandlers, AbstractBlackListParserHandler::getHandleClientType);

        log.info("【⭐权限引擎初始化】成功注册鉴权处理器数量: {}", clientAuthHandlerMap.size());
        log.info("【⭐权限引擎初始化】成功注册黑名单处理器数量: {}", clientBlackListMap.size());
    }

    public AbstractClientAuthHandler findAuthHandlerByClientKeyEnum(McpSupportClientTypeEnums clientTypeEnum){
        return this.clientAuthHandlerMap.get(clientTypeEnum);
    }

    public AbstractBlackListParserHandler findBlackListHandlerByClientKeyEnum(McpSupportClientTypeEnums clientTypeEnum){
        return this.clientBlackListMap.get(clientTypeEnum);
    }

    /**
     * 通用转换方法，并防止重复 Key 导致启动报错
     */
    private <T> Map<McpSupportClientTypeEnums, T> safeToMap(List<T> list, Function<T, McpSupportClientTypeEnums> keyExtractor) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(
                keyExtractor,
                Function.identity(),
                (existing, replacement) -> {
                    // 如果同一个枚举被多个 Handler 实现，启动时直接警告并保留先加载的
                    log.warn("发现重复的 Handler 实现，枚举: {}, 将被忽略: {}",
                            existing.getClass().getSimpleName(), replacement.getClass().getSimpleName());
                    return existing;
                }
        ));
    }
}
