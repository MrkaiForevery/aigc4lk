package com.air.mcp.gateway.auth.handler;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import com.air.mcp.gateway.model.auth.AuthBlackListMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DifyBlackListParserHandler extends AbstractBlackListParserHandler{

    private final JsonMapper jsonMapper;
    private static final McpSupportClientTypeEnums clientTypeEnum = McpSupportClientTypeEnums.DIFY_AGENT_CLIENT_TYPE;


    @Override
    public Map<String,List<String>> parserBlackList(List<AuthBlackListMapping> baseBlackList) {
        //这里是以key=elementCode value=list<UserId> 为map结构
        Map<String,List<String>> userIdMap = new HashMap<>();
        if (baseBlackList == null || baseBlackList.isEmpty()) {
            return userIdMap;
        }
        for (AuthBlackListMapping black : baseBlackList) {
            PGobject blackMappingKeyJson = black.getBlackMappingKeyJson();
            if (blackMappingKeyJson == null || blackMappingKeyJson.getValue() == null) continue;

            try {
                JsonNode rootNode = jsonMapper.readTree(blackMappingKeyJson.getValue());
                JsonNode listNode = rootNode.get("user_id_list");
                if (listNode != null && listNode.isArray()) {
                    List<String> userIdsElement = new ArrayList<>();
                    for (JsonNode node : listNode) {
                        userIdsElement.add(node.asText());
                    }
                    userIdMap.put(black.getDenyTargetElementCodes(),userIdsElement);
                }
            } catch (Exception e) {
                // 处理异常，建议打日志
                log.error("解析黑名单JSON失败: {}", blackMappingKeyJson.getValue(), e);
            }
        }
        return userIdMap;
    }

    @Override
    public McpSupportClientTypeEnums getHandleClientType() {
        return this.clientTypeEnum;
    }

    @Override
    public Set<String> doFilter(List<String> userIdJsonList, Map<String, List<String>> allBaseBlackMap, Set<String> baseElementCodeSet) {
        Set<String> userList = new HashSet<>();
        for (String userJson : userIdJsonList) {
            Set<String> stringSet = extractUserIds(userJson);
            userList.addAll(stringSet);
        }
        
        Set<String> flagRemoveElementCodeSet = new HashSet<>();
        
        allBaseBlackMap.forEach((key,value) ->{
             boolean isFilter = value.stream().anyMatch(userList::contains);
             if (isFilter){
                 flagRemoveElementCodeSet.add(key);
             }
        });

        return baseElementCodeSet.stream()
                .filter(element -> !flagRemoveElementCodeSet.contains(element))
                .collect(Collectors.toSet());
    }

    private Set<String> extractUserIds(String userJson) {
        Set<String> userIds = new HashSet<>();
        if (userJson == null || userJson.isBlank()) {
            return userIds;
        }

        try {
            JsonNode rootNode = jsonMapper.readTree(userJson);
            JsonNode listNode = rootNode.get("userIdlist");

            // 防御性判断：确保节点存在且是一个数组
            if (listNode != null && listNode.isArray()) {
                for (JsonNode node : listNode) {
                    userIds.add(node.asText()); // 自动转为 String
                }
            }
        } catch (Exception e) {
            // 记录解析异常日志
        }
        return userIds;
    }
}
