package com.air.memory.repository.unstructured;


import com.air.memory.entity.*;
import com.air.memory.mapper.*;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 使用redis存储会话记忆
 */
@Component
@RequiredArgsConstructor
public class SessionMemoryRepository {

    private final RedissonClient redissonClient;

    // ---------- 会话记忆 (Redisson) ----------
    public List<String> getSessionMessages(String sessionId) {
        RList<String> list = redissonClient.getList("memory:session:" + sessionId);
        return list.readAll();
    }

    public void appendSessionMessage(String sessionId, String message) {
        RList<String> list = redissonClient.getList("memory:session:" + sessionId);
        list.add(message);
        if (list.size() > 20) list.remove(0);
        list.expire(Duration.ofMinutes(30));
    }

    public void clearSession(String sessionId) {
        redissonClient.getBucket("memory:session:" + sessionId).delete();
    }

}