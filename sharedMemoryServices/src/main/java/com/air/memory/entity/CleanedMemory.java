package com.air.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 清洗后的记忆结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanedMemory {

    /** 原始内容 */
    private String rawContent;

    /** 是否有效（false 表示应该丢弃） */
    private boolean valid;

    /** 清洗后的摘要 */
    private String summary;

    /** 提取的知识点（JSON 格式） */
    private Map<String, Object> knowledge;

    /** 提取的用户画像标签 */
    private List<String> profileTags;

    /** 记忆类型：IDENTITY / PROFILE / BEHAVIOR / KNOWLEDGE */
    private String memoryType;

    /** 置信度 (0.0 - 1.0) */
    private double confidence;

    /** 清洗时间 */
    private long cleanedAt;

    /** 清洗来源：RULE_BASED / LLM_BASED */
    private String cleanSource;

    /** 备注（清洗过程中的警告信息） */
    private String remark;
}