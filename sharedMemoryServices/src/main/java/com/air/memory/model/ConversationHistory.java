package com.air.memory.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

 /**
 * conversation_history;会话历史记录表数据表的PO对象
 * @author : mrkai
 * @date : 2026-6-17
 */

 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 @TableName("conversation_history")
public class ConversationHistory implements Serializable,Cloneable{
    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private int id ;
    /** 会话id,; */
    private String threadId ;
    /** 用户id,; */
    private String userId ;
    /** 会话状态,IN_PROGRESS / INTERRUPTED / COMPLETED / FAILED,; */
    private String status ;
    /** 创建人,; */
    private String createdBy ;
    /** 创建时间,; */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt ;
    /** 修改人,; */
    private String updateBy ;
    /** 修改时间,; */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updateAt ;
}