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

// 身份记忆实体
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("memory_identity")
public class IdentityMemory {

    @TableId(value = "user_id")
    private String userId;
    private String username;
    private String phone;
    private String email;
    private String memberLevel;
    private String role;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}








