package com.air.platform.common.tranfer;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * Commander 统一响应模型
 * 
 * 同时支持：
 * 1. Commander 内部通道通信（配合 CommanderChannel 使用）
 * 2. 对外 API 返回（配合 CommanderController 使用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommanderResponse {

    // ========== 基础字段 ==========
    /** 执行ID（对外API使用） */
    private String executionId;
    
    /** 任务ID（内部通道通信使用） */
    private String taskId;
    
    /** 是否成功 */
    private boolean success;
    
    /** 是否降级执行 */
    private boolean fallback;
    
    /** 错误码 */
    private String errorCode;
    
    /** 错误消息 */
    private String errorMessage;
    
    /** 执行结果 */
    private Map<String, Object> result;
    
    /** 架构信息（对外API使用） */
    private ArchitectureInfo architectureUsed;
    
    /** 使用的架构ID（内部通道通信使用） */
    private String architectureId;
    
    /** 模型信息（对外API使用） */
    private ModelInfo modelUsed;
    
    /** 使用的模型ID（内部通道通信使用） */
    private String modelId;
    
    /** 处理耗时（毫秒） */
    private long processingTimeMs;
    
    /** 场景 */
    private String scenario;
    
    /** 复杂度 */
    private String complexity;

    /** 当前阶段（流式输出时使用） */
    private String stage;


    // ========== 流式输出判断专用 ==========

    /** 是否为进度事件 */
    public boolean isProgress() {
        return stage != null && !success;
    }

    /** 是否为最终结果 */
    public boolean isFinal() {
        return stage == null && success;
    }

    // ========== 内部类 ==========
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArchitectureInfo {
        private String architectureId;
        private String architectureType;
        private String selectionReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModelInfo {
        private String modelId;
        private String modelName;
        private String provider;
        private String selectionStrategy;
    }

    // ========== 手动 Builder ==========

    public static CommanderResponseBuilder builder() {
        return new CommanderResponseBuilder();
    }

    public static class CommanderResponseBuilder {
        private String executionId;
        private String taskId;
        private boolean success;
        private boolean fallback;
        private String errorCode;
        private String errorMessage;
        private Map<String, Object> result;
        private ArchitectureInfo architectureUsed;
        private String architectureId;
        private ModelInfo modelUsed;
        private String modelId;
        private long processingTimeMs;
        private String scenario;
        private String complexity;
        private String stage;

        public CommanderResponseBuilder stage(String stage) {
            this.stage = stage;
            return this;
        }

        public CommanderResponseBuilder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public CommanderResponseBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public CommanderResponseBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public CommanderResponseBuilder fallback(boolean fallback) {
            this.fallback = fallback;
            return this;
        }

        public CommanderResponseBuilder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public CommanderResponseBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public CommanderResponseBuilder result(Map<String, Object> result) {
            this.result = result;
            return this;
        }

        public CommanderResponseBuilder architectureUsed(ArchitectureInfo architectureUsed) {
            this.architectureUsed = architectureUsed;
            return this;
        }

        public CommanderResponseBuilder architectureId(String architectureId) {
            this.architectureId = architectureId;
            return this;
        }

        public CommanderResponseBuilder modelUsed(ModelInfo modelUsed) {
            this.modelUsed = modelUsed;
            return this;
        }

        public CommanderResponseBuilder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public CommanderResponseBuilder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }

        public CommanderResponseBuilder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        public CommanderResponseBuilder complexity(String complexity) {
            this.complexity = complexity;
            return this;
        }

        public CommanderResponse build() {
            CommanderResponse response = new CommanderResponse();
            response.executionId = this.executionId;
            response.taskId = this.taskId;
            response.success = this.success;
            response.fallback = this.fallback;
            response.errorCode = this.errorCode;
            response.errorMessage = this.errorMessage;
            response.result = this.result;
            response.architectureUsed = this.architectureUsed;
            response.architectureId = this.architectureId;
            response.modelUsed = this.modelUsed;
            response.modelId = this.modelId;
            response.processingTimeMs = this.processingTimeMs;
            response.scenario = this.scenario;
            response.complexity = this.complexity;
            response.stage = this.stage;
            return response;
        }
    }

    // ========== 静态工厂方法 ==========

    /**
     * 成功响应（对外 API）- 使用 ArchitectureInfo 和 ModelInfo
     */
    public static CommanderResponse success(
            String executionId,
            Map<String, Object> result,
            ArchitectureInfo architecture,
            ModelInfo model,
            String scenario,
            String complexity,
            long durationMs) {
        return CommanderResponse.builder()
                .executionId(executionId)
                .success(true)
                .fallback(false)
                .result(result)
                .architectureUsed(architecture)
                .modelUsed(model)
                .scenario(scenario)
                .complexity(complexity)
                .processingTimeMs(durationMs)
                .build();
    }

    /**
     * 成功响应（内部通道）- 使用字符串形式的 architectureId 和 modelId
     */
    public static CommanderResponse success(
            String taskId,
            Map<String, Object> result,
            String architectureId,
            String modelId) {
        return CommanderResponse.builder()
                .taskId(taskId)
                .executionId(taskId)
                .success(true)
                .fallback(false)
                .result(result)
                .architectureId(architectureId)
                .modelId(modelId)
                .processingTimeMs(System.currentTimeMillis())
                .build();
    }

    /**
     * 降级响应
     */
    public static CommanderResponse fallback(
            String executionId,
            Map<String, Object> result,
            String originalError,
            long durationMs) {
        return CommanderResponse.builder()
                .executionId(executionId)
                .success(true)
                .fallback(true)
                .result(result)
                .errorMessage("Fallback due to: " + originalError)
                .processingTimeMs(durationMs)
                .build();
    }

    /**
     * 错误响应（对外 API）
     */
    public static CommanderResponse error(String executionId, String errorMessage) {
        return CommanderResponse.builder()
                .executionId(executionId)
                .success(false)
                .fallback(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 错误响应（内部通道，带错误码）
     */
    public static CommanderResponse error(String taskId, String errorCode, String errorMessage) {
        return CommanderResponse.builder()
                .taskId(taskId)
                .executionId(taskId)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}