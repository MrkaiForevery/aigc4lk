package com.air.mcp.gateway.service.tool.identity;

import com.air.mcp.gateway.annotation.meta.CustomerMcpToolMeta;
import com.air.mcp.gateway.annotation.permission.RequireMcpToolPermission;
import com.air.mcp.gateway.dto.BaseCodeCheckResultDTO;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeTestTools {

    @McpTool(name = "211100000_1001", description = "查询项目信息列表")
    @CustomerMcpToolMeta(domainCode = "210000000", sensitivityType = "read", elementCode = "211100000_1001")
    @RequireMcpToolPermission(toolCode = "211100000_1001", toolName = "queryProjectBaseInfoList")
    public List<BaseCodeCheckResultDTO> queryProjectBaseInfoList(
            @McpToolParam(description = "用户身份ID", required = true) String userId,
            @McpToolParam(description = "系统标识", required = true) String systemId,
            McpTransportContext ctx) {
        //todo 这里先模拟返回
        BaseCodeCheckResultDTO dto1 = BaseCodeCheckResultDTO.builder()
                .userId(userId)
                .name("牛批1号")
                .age(18)
                .systemId(systemId)
                .system("吹牛系统")
                .description("这是一个负责专门吹牛的强大的系统！！！")
                .build();

        BaseCodeCheckResultDTO dto2 = BaseCodeCheckResultDTO.builder()
                .userId(userId)
                .name("牛批2号")
                .age(18)
                .systemId(systemId)
                .system("吹牛2系统")
                .description("这同样也是一个负责专门吹牛的强大的系统！！！")
                .build();

        ArrayList<BaseCodeCheckResultDTO> resultDTOS = new ArrayList<>();
        resultDTOS.add(dto1);
        resultDTOS.add(dto2);
        return resultDTOS;
    }

}
