package com.air.memory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//行为记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_behavior")
public class BehaviorRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String sessionId;
    private String actionType;
    private String content;
    private String metadata;          // JSON
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}