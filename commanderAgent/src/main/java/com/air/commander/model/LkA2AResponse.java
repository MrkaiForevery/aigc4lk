package com.air.commander.model;   // A2A请求/响应模型（内部类简化）

import com.alibaba.cloud.ai.graph.action.Command;
import io.a2a.spec.Message;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * A2A返回包装实体
 */
@Data
public class LkA2AResponse {
    private List<Message> messages;          // 响应消息列表（通常是 agent 回复）
    private Command command;                 // 中断命令（可选）
    private Map<String, Object> metadata;    // 附加元数据
}