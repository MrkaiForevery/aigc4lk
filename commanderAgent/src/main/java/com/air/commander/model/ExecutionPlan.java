package com.air.commander.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 执行计划实体
 */
@Data
@Builder
public class ExecutionPlan {

    /**生成执行plan采用的方式:template(采用预定义的模板进行匹配，对应严格场景)/dynamic(使用LLM分析生成-对应非严格场景)**/
    private ModeType mode;

    private String planId;
    private List<ExecutionResult> results;
    private boolean interrupted;
    private String summary;
    private String xid;
    private Map<String, Object> context;

    public enum ModeType {
        TEMPLATE, DYNAMIC
    }
}