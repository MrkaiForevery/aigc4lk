package com.air.customerServiceAgent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class OrderQueryTools {

    @Tool(description = "根据订单号查询订单详情")
    public Map<String, Object> queryOrder(@ToolParam(description = "订单号") String orderId) {
        // 模拟调用订单中心 API
        // 实际应通过 Feign 调用订单微服务
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("status", "已发货");
        result.put("trackingNumber", "SF1234567890");
        result.put("estimatedDelivery", "2026-06-10");
        return result;
    }
}