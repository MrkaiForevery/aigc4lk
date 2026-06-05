package com.air.commander.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConversationManager {

    private final RedissonClient redissonClient;
    
    private static final int MAX_MESSAGES = 20;
    private static final int TTL_MINUTES = 30;

    /**
     * 添加一条消息到指定 threadId 的会话
     */
    public void addMessage(String threadId, String role, String content) {
        String key = "conv:" + threadId;
        RList<Message> list = redissonClient.getList(key);
        
        // 添加消息对象（Redisson 自动将对象序列化为 JSON 存储）
        list.add(new Message(role, content));
        
        // 如果超过最大消息数，只保留最近 MAX_MESSAGES 条（裁剪列表尾部）
        if (list.size() > MAX_MESSAGES) {
            // trim(开始索引, 结束索引)：保留从 -MAX_MESSAGES 到 -1（最后20条）
            list.trim(-MAX_MESSAGES, -1);
        }
        
        // 每次写入后刷新整个列表的过期时间
        list.expire(Duration.ofMinutes(TTL_MINUTES));
    }

    /**
     * 获取指定 threadId 的最近 n 条消息
     */
    public List<Message> getRecentMessages(String threadId, int n) {
        String key = "conv:" + threadId;
        RList<Message> list = redissonClient.getList(key);
        
        // range 直接返回指定范围的 List<Message>，索引负数表示从尾部计算
        return list.range(-n, -1);
    }

    // ---------- 内部消息模型 ----------
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}