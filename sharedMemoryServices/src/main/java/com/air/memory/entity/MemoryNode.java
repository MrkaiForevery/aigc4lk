package com.air.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统一记忆节点（用于排序）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryNode {

    /** 记忆类型：IDENTITY / PROFILE / BEHAVIOR / KNOWLEDGE / PREFERENCE todo 后期改造成枚举*/
    private String memoryType;

    /** 记忆内容 */
    private String content;

    /** 记忆时间戳 */
    private LocalDateTime timestamp;

    /** 使用频率（被检索的次数） */
    private int frequency;

    /** 相似度（仅向量搜索结果有效） */
    private double similarity;

    /** 元数据 */
    private Map<String, Object> metadata;

    /** 来源标识 */
    private String sourceId;
}