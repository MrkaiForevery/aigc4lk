package com.air.customerServiceAgent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class TicketQueryTools {

    @Tool(description = "查询用户的历史工单列表")
    public List<Map<String, Object>> queryTickets(@ToolParam(description = "用户ID") String userId) {
        // 模拟工单系统 API
        return Collections.emptyList(); // 实际返回工单列表
    }
    
    @Tool(description = "查询指定工单的详细进度")
    public Map<String, Object> queryTicketDetail(@ToolParam(description = "工单号") String ticketId) {
        // 模拟
        return Map.of("ticketId", ticketId, "status", "处理中", "handler", "技术支持组");
    }
}