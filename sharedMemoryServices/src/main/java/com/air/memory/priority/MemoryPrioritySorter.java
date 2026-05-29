package com.air.memory.priority;

import com.air.memory.entity.MemoryNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 记忆优先级排序器
 * 
 * 排序规则（从高到低）：
 * 1. 类型优先级：PROFILE > BEHAVIOR > KNOWLEDGE
 * 2. 时间戳：越近越优先
 * 3. 频率：越频繁越优先
 * 4. 相似度：越相似越优先（仅向量搜索结果）
 */
@Slf4j
@Component
public class MemoryPrioritySorter {

    /** 类型权重 */
    private static final int PROFILE_WEIGHT = 100;
    private static final int BEHAVIOR_WEIGHT = 80;
    private static final int KNOWLEDGE_WEIGHT = 50;
    private static final int DEFAULT_WEIGHT = 30;

    /**
     * 对记忆列表排序（降序：优先级高的在前）
     */
    public List<MemoryNode> sort(List<MemoryNode> memories) {
        return memories.stream()
                .sorted(this::compare)
                .toList();
    }

    /**
     * 排序并截取前 N 条
     */
    public List<MemoryNode> sortAndLimit(List<MemoryNode> memories, int limit) {
        return memories.stream()
                .sorted(this::compare)
                .limit(limit)
                .toList();
    }

    /**
     * 比较两条记忆的优先级
     * 返回负数表示 a 优先级更高（排在前面）
     */
    private int compare(MemoryNode a, MemoryNode b) {
        // 1. 类型优先级
        int typeCompare = Integer.compare(getTypeWeight(b), getTypeWeight(a));
        if (typeCompare != 0) return typeCompare;

        // 2. 时间戳（越新越优先）
        if (a.getTimestamp() != null && b.getTimestamp() != null) {
            int timeCompare = a.getTimestamp().compareTo(b.getTimestamp());
            if (timeCompare != 0) return -timeCompare;
        }

        // 3. 使用频率（越频繁越优先）
        int freqCompare = Integer.compare(b.getFrequency(), a.getFrequency());
        if (freqCompare != 0) return freqCompare;

        // 4. 相似度（越相似越优先）
        return Double.compare(b.getSimilarity(), a.getSimilarity());
    }

    /**
     * 根据记忆类型获取权重
     */
    private int getTypeWeight(MemoryNode memory) {
        if (memory.getMemoryType() == null) return DEFAULT_WEIGHT;
        return switch (memory.getMemoryType().toUpperCase()) {
            case "PROFILE" -> PROFILE_WEIGHT;
            case "BEHAVIOR" -> BEHAVIOR_WEIGHT;
            case "KNOWLEDGE" -> KNOWLEDGE_WEIGHT;
            default -> DEFAULT_WEIGHT;
        };
    }
}