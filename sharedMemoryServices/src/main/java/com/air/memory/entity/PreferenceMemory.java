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

// 偏好记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_preference")
public class PreferenceMemory {
    @TableId(value = "user_id")
    private String userId;
    private String outputStyle;
    private Boolean showIntermediate;
    private String preferredModels;   // JSON
    private Boolean autoFallback;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;
}