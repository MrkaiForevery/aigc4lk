package com.air.mcp.gateway.model.identity;

import com.air.mcp.gateway.enums.McpSupportClientTypeEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthMcpCommonInfo {

    private McpSupportClientTypeEnums clientTypeEnum;
    private List<String> blackMappingKeyJsonList;
    private List<String> clientMappingKeyJsonList;
}

