package com.air.memory.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

 /**
 * execution_result_history;执行结果历史记录表数据表的PO对象
 * @author : http://www.yonsum.com
 * @date : 2026-6-17
 */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 @TableName("execution_result_history")
public class ExecutionResultHistory implements Serializable,Cloneable{
    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private int id ;
    /** 关联的编排计划id,; */
    private String relationPlanId ;
    /** 步骤id,; */
    private String stepId ;
    /** 步骤结果jsonb对象,; */
    private String resultContentJsonb ;
    /** 执行状态,成功，失败，阻塞,; */
    private String executeStatus ;
    /** 执行耗时,; */
    private long executeTime ;
    /** 创建人,; */
    private String createdBy ;
    /** 创建时间,; */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime ;
    /** 修改人,; */
    private String updateBy ;
    /** 修改时间,; */
    private LocalDateTime updateTime ;

}