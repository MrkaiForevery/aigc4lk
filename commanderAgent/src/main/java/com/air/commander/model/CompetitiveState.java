package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 竞争状态（用于恢复时重建执行现场）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitiveState implements Serializable {
    private int groupIndex;                          // 当前竞争组索引
    private String currentGroupId;                   // 当前竞争组 ID
    private String interruptedCompetitorId;          // 被中断的竞争者 ID
    private List<String> completedCompetitors;       // 已完成的竞争者 ID 列表
}