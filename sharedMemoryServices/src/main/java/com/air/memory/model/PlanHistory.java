package com.air.memory.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDateTime;

 /**
 * plan_history;生成的编排计划历史记录表数据表的PO对象
 * @author : mrkai
 * @date : 2026-6-17
 */
 @Data
 @Builder
 @NoArgsConstructor
 @AllArgsConstructor
 @TableName("plan_history")
public class PlanHistory implements Serializable,Cloneable{
    /** 主键id,; */
    @TableId(type = IdType.AUTO)
    private Integer id ;
    /** 会话id,; */
    private String threadId ;
    /** 关联的请求id,; */
    private String relationRequestId ;
    /** 关联的严格场景的模板id,; */
    private String relationTemplateId ;
     /** 用户的原始输入,; */
     private String userInput ;
    /** 编排计划id,; */
    private String planId ;
    /** 编排计划的jsonb对象,; */
    private PGobject planContentJsonb ;
    /** 执行到哪一步的step_id,; */
    private String executeStepId ;
    /** 创建人,; */
    private String createdBy ;
    /** 创建时间,; */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime ;
    /** 修改人,; */
    private String updateBy ;
    /** 修改时间,; */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime ;

     // 方便设置 JSON 字符串的方法
     public void setPlanContentJsonbString(String json) throws SQLException {
         if (this.planContentJsonb == null) {
             this.planContentJsonb = new PGobject();
         }
         this.planContentJsonb.setType("jsonb");
         this.planContentJsonb.setValue(json);
     }

     // 方便获取 JSON 字符串的方法
     public String getPlanContentJsonbString() {
         return this.planContentJsonb == null ? null : this.planContentJsonb.getValue();
     }
}