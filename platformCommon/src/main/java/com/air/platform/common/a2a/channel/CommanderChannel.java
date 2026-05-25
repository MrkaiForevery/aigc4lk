package com.air.platform.common.a2a.channel;

import com.air.platform.common.a2a.protocol.A2AResponse;
import com.air.platform.common.tranfer.CommanderResponse;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CommanderChannel {

    private final Map<String, CommanderTask> pendingTasks = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<CommanderResponse>> taskFutures = new ConcurrentHashMap<>();

    /**
     * 提交Commander任务
     */
    public CompletableFuture<CommanderResponse> submitTask(CommanderTask task) {
        String taskId = task.getTaskId() != null ? task.getTaskId() : UUID.randomUUID().toString();
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PENDING);
        task.setCreatedAt(System.currentTimeMillis());

        pendingTasks.put(taskId, task);

        CompletableFuture<CommanderResponse> future = new CompletableFuture<>();
        taskFutures.put(taskId, future);

        log.info("Task submitted to Commander channel: {} ({})", taskId, task.getScenario());
        return future;
    }

    /**
     * 报告任务结果
     */
    public CommanderResponse reportResult(String taskId, Map<String, Object> result) {
        CommanderTask task = pendingTasks.get(taskId);
        if (task == null) {
            log.warn("Task not found: {}", taskId);
            return CommanderResponse.error(taskId, "CMD-001", "Task not found");
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(System.currentTimeMillis());

        // 使用统一的成功工厂方法
        CommanderResponse response = CommanderResponse.success(
                taskId,
                result,
                task.getArchitectureId(),
                task.getModelId()
        );
        response.setProcessingTimeMs(task.getCompletedAt() - task.getCreatedAt());

        CompletableFuture<CommanderResponse> future = taskFutures.remove(taskId);
        if (future != null) {
            future.complete(response);
        }

        log.info("Task completed: {} in {}ms", taskId, response.getProcessingTimeMs());
        return response;
    }

    /**
     * 报告任务失败
     */
    public CommanderResponse reportFailure(String taskId, String errorCode, String errorMessage) {
        CommanderTask task = pendingTasks.get(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.FAILED);
            task.setCompletedAt(System.currentTimeMillis());
            task.setErrorMessage(errorMessage);
        }

        // 使用统一的错误工厂方法
        CommanderResponse response = CommanderResponse.error(taskId, errorCode, errorMessage);
        if (task != null) {
            response.setProcessingTimeMs(task.getCompletedAt() - task.getCreatedAt());
        }

        CompletableFuture<CommanderResponse> future = taskFutures.remove(taskId);
        if (future != null) {
            future.complete(response);
        }

        log.error("Task failed: {} - {}: {}", taskId, errorCode, errorMessage);
        return response;
    }

    /**
     * 获取任务状态
     */
    public CommanderTask getTaskStatus(String taskId) {
        return pendingTasks.get(taskId);
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        CommanderTask task = pendingTasks.get(taskId);
        if (task != null && task.getStatus() == TaskStatus.PENDING) {
            task.setStatus(TaskStatus.CANCELLED);
            reportFailure(taskId, "CMD-005", "Task cancelled by user");
            return true;
        }
        return false;
    }

    // ==================== 内部类 ====================

    @Data
    @Builder
    public static class CommanderTask {
        private String taskId;
        private String scenario;
        private String architectureId;
        private String modelId;
        private ModalityType modality;
        private Map<String, Object> input;
        private Map<String, Object> context;
        private TaskStatus status;
        private long createdAt;
        private Long completedAt;
        private String errorMessage;

        public boolean isMultimodal() {
            return modality != null && modality != ModalityType.TEXT;
        }

        public ModalityType getRequiredModality() {
            return modality;
        }

        public Map<String, Object> getModalityTask() {
            return input;
        }

        public void enrichWithModalityResult(A2AResponse modalityResult) {
            if (context == null) {
                context = new java.util.HashMap<>();
            }
            context.put("modality_result", modalityResult.getPayload());
        }
    }

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    public enum ModalityType {
        TEXT, VISION, SPEECH, VIDEO, MULTIMODAL
    }

    /**
     * 架构选择结果（内部调度使用）
     */
    @Data
    @Builder
    public static class ArchitectureSelection {
        /** 架构ID */
        private String architectureId;
        /** 架构类型 */
        private String architectureType;
        /** 选择的模型ID */
        private String modelId;
        /** 选择原因 */
        private String selectionReason;
        /** 场景 */
        private String scenario;
        /** 复杂度 */
        private String complexity;
    }
}