package com.air.customerServiceAgent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class RoutePolicyTools {

    @Tool(description = "根据问题分类结果返回路由目标（售后/技术/投诉/人工）")
    public String routeIssue(@ToolParam(description = "问题分类标签") String category,
                             @ToolParam(description = "用户ID") String userId) {
        switch (category.toLowerCase()) {
            case "售后": return "after_sales_queue";
            case "技术": return "technical_support";
            case "投诉": return "complaint_escalation";
            default: return "general_chat";
        }
    }
}