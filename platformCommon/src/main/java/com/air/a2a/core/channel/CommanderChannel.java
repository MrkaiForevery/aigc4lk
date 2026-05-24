package com.air.a2a.core.channel;

import com.air.a2a.core.protocol.A2AResponse;
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
            return CommanderResponse.builder()
                .taskId(taskId)
                .success(false)
                .errorCode("CMD-001")
                .errorMessage("Task not found")
                .build();
        }
        
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(System.currentTimeMillis());
        
        CommanderResponse response = CommanderResponse.builder()
            .taskId(taskId)
            .success(true)
            .result(result)
            .architectureUsed(task.getArchitectureId())
            .processingTimeMs(task.getCompletedAt() - task.getCreatedAt())
            .build();
        
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
        
        CommanderResponse response = CommanderResponse.builder()
            .taskId(taskId)
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .processingTimeMs(task != null ? task.getCompletedAt() - task.getCreatedAt() : 0)
            .build();
        
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
    
    @Data
    @Builder
    public static class CommanderResponse {
        private String taskId;
        private boolean success;
        private Map<String, Object> result;
        private String architectureUsed;
        private String modelUsed;
        private long processingTimeMs;
        private String errorCode;
        private String errorMessage;
        
        public static CommanderResponse success(String taskId, Map<String, Object> result, 
                                                  ArchitectureSelection selection) {
            return CommanderResponse.builder()
                .taskId(taskId)
                .success(true)
                .result(result)
                .architectureUsed(selection != null ? selection.getArchitectureId() : null)
                .modelUsed(selection != null ? selection.getModelId() : null)
                .processingTimeMs(System.currentTimeMillis())
                .build();
        }
    }
    
    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }
    
    public enum ModalityType {
        TEXT, VISION, SPEECH, VIDEO, MULTIMODAL
    }
    
    @Data
    @Builder
    public static class ArchitectureSelection {
        private String architectureId;
        private String modelId;
        private String selectionReason;
    }
}