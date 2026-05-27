package com.air.memory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 画像记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_profile")
public class ProfileMemory {
    @TableId(value = "user_id")
    private String userId;
    private String preferredModel;
    private String preferredArchitecture;
    private String technicalLevel;
    private String topicsOfInterest;   // JSON
    private String communicationStyle;
    private String extraAttrs;         // JSON
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;
}