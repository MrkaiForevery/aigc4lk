package com.air.commander.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.seata.tm.api.transaction.SuspendedResourcesHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.*;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * check-point机制下断点记录实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterruptContext implements Serializable {

    // ==================== 身份标识 ====================
    private String xid;                     // Seata 全局事务 ID
    private String userId;                  // 发起任务的用户
    private String threadId;                // 会话 ID

    // ==================== 执行位置 ====================
    private String planId;                  // 当前编排计划的 ID
    private String currentStepId;           // 中断发生在哪一步
    private int stepIndex;                  // 当前步骤在步骤列表中的索引（用于恢复时跳过已完成步骤）
    private String planJson;                // 整个编排计划的 JSON 快照，用于崩溃恢复时重新加载计划

    // ==================== 业务上下文 ====================
    private String commandType;             // REQUEST_CREDENTIAL / REQUEST_CONFIRM
    private String question;                // 向用户展示的问题
    private List<String> requiredScopes;    // 需要用户授予的权限范围
    private Map<String, Object> runtimeContext; // 前序步骤的输出结果 (key: stepId.output)

    // ==================== 场景模式 ====================
    private ExecutionPlan.ModeType mode;  // "template" 或 "dynamic"

    // ==================== 元数据 ====================
    private Instant createdAt;              // 检查点创建时间
    private Instant timeoutAt;              // 超时时间（通常为创建时间 + 30 分钟）

}