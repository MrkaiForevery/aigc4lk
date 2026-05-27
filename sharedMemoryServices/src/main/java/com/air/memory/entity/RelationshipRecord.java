package com.air.memory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 关系记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_relationship")
public class RelationshipRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String relatedUser;
    private String relationType;
    private String projectId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}