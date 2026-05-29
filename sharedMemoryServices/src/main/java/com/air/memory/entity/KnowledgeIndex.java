package com.air.memory.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识文档索引实体
 * 用于记录 Chroma 中每个文档的元数据，辅助低频降级
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_index")
public class KnowledgeIndex {

    /** Chroma 文档 ID */
    @TableId(value = "doc_id")
    private String docId;

    /** 用户 ID */
    @TableField("user_id")
    private String userId;

    /** 来源 */
    @TableField("source")
    private String source;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 最后访问时间 */
    @TableField("last_access")
    private LocalDateTime lastAccess;

    /** 状态：active（活跃）/ archived（已归档） */
    @TableField("status")
    private String status;
}