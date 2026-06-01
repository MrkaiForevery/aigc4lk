package com.air.commander.agent;

import com.air.commander.entity.*;
import com.air.commander.graph.DynamicGraphBuilder;
import com.air.commander.graph.GraphTemplateSelector;
import com.air.commander.intent.IntentClassifier;
import com.air.commander.model.ChatModelRouter;
import com.air.platform.common.tranfer.CommanderResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommanderAgent {

    private final IntentClassifier intentClassifier;
    private final ChatModelRouter chatModelRouter;
    private final GraphTemplateSelector templateSelector;
    private final DynamicGraphBuilder dynamicGraphBuilder;

    private final Map<String, ExecutionRecord> executionHistory = new ConcurrentHashMap<>();

    // ==================== 同步执行 ====================
    @CircuitBreaker(name = "architecture-execution", fallbackMethod = "fallbackExecute")
    @Retry(name = "commander-retry")
    public CommanderResponse execute(CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.info("🚀 [{}] Commander execution started", executionId);

        try {
            // 1. 意图识别
            IntentAnalysis intent = intentClassifier.analyzeIntent(request.getUserInput());
            log.info("🎯 [{}] Intent: scenario={}, complexity={}, modality={}",
                    executionId, intent.getScenario(), intent.getComplexity(), intent.getModality());

            // 2. 选择 Graph 模板
            String templateId = templateSelector.selectTemplate(intent);
            log.info("🏗️ [{}] Graph template selected: {}", executionId, templateId);

            // 3. 模型选择（用于记录和 state 传递）
            ModelSelection model = chatModelRouter.selectModel(intent, null);

            // 4. 构建执行输入
            Map<String, Object> executionInput = buildExecutionInput(request, intent, executionId);
            executionInput.put("model_id", model.getModelId());
            executionInput.put("complexity", intent.getComplexity());
            executionInput.put("template_id", templateId);

            // 5. 编译并执行 Graph
            Map<String, Object> result = dynamicGraphBuilder.compileAndExecute(templateId, executionInput);

            // 6. 计算耗时
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();

            // 7. 记录
            recordExecution(executionId, request, intent, templateId, model, durationMs, true, false);

            log.info("✅ [{}] Commander execution completed in {}ms", executionId, durationMs);

            return buildSuccessResponse(executionId, result, templateId, model, intent, durationMs);

        } catch (Exception e) {
            log.error("❌ [{}] Commander execution failed", executionId, e);
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            recordExecution(executionId, request, null, null, null, durationMs, false, false);
            throw new CommanderExecutionException("Execution failed: " + e.getMessage(), e);
        }
    }

    // ==================== 降级 ====================
    public CommanderResponse fallbackExecute(CommanderRequest request, Throwable t) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.warn("🔄 [{}] Fallback triggered: {}", executionId, t.getMessage());

        try {
            // 使用最简单的单次 LLM 调用模板
            String fallbackTemplateId = "single-llm-call";

            Map<String, Object> input = new java.util.HashMap<>();
            input.put("query", request.getUserInput());
            input.put("model_id", "qwen-turbo");
            input.put("complexity", "LOW");
            input.put("execution_id", executionId + "-fallback");
            input.put("fallback", true);

            Map<String, Object> result = dynamicGraphBuilder.compileAndExecute(fallbackTemplateId, input);

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();

            // 降级记录
            ModelSelection fallbackModel = ModelSelection.builder()
                    .modelId("qwen-turbo").modelName("qwen-turbo").provider("alibaba").build();
            recordExecution(executionId, request, null, fallbackTemplateId, fallbackModel, durationMs, true, true);

            return CommanderResponse.fallback(executionId, result, t.getMessage(), durationMs);

        } catch (Exception e) {
            log.error("❌ [{}] Fallback also failed", executionId, e);
            return CommanderResponse.error(executionId,
                    "Primary: " + t.getMessage() + "; Fallback: " + e.getMessage());
        }
    }

    // ==================== 流式执行 ====================
    public Flux<CommanderResponse> executeStream(CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        return Flux.create(sink -> {
            try {
                // 1. 意图识别
                pushProgress(sink, executionId, "INTENT_ANALYSIS");
                IntentAnalysis intent = intentClassifier.analyzeIntent(request.getUserInput());

                // 2. 模板选择
                pushProgress(sink, executionId, "TEMPLATE_SELECTION");
                String templateId = templateSelector.selectTemplate(intent);

                // 3. 模型选择
                pushProgress(sink, executionId, "MODEL_SELECTION");
                ModelSelection model = chatModelRouter.selectModel(intent, null);

                // 4. 构建输入
                Map<String, Object> executionInput = buildExecutionInput(request, intent, executionId);
                executionInput.put("model_id", model.getModelId());
                executionInput.put("complexity", intent.getComplexity());

                // 5. 执行
                pushProgress(sink, executionId, "TASK_EXECUTION");
                Map<String, Object> result = dynamicGraphBuilder.compileAndExecute(templateId, executionInput);

                long durationMs = Duration.between(startTime, Instant.now()).toMillis();

                CommanderResponse response = buildSuccessResponse(executionId, result, templateId, model, intent, durationMs);
                sink.next(response);
                sink.complete();

            } catch (Exception e) {
                log.error("❌ [{}] Stream execution failed", executionId, e);
                sink.error(e);
            }
        });
    }

    /**
     * Token 级别流式输出 - 同样基于动态 Graph，但需特殊处理流式结果
     */
    public Flux<String> executeTokenStream(CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();

        return Mono.fromCallable(() -> {
                    IntentAnalysis intent = intentClassifier.analyzeIntent(request.getUserInput());
                    String templateId = templateSelector.selectTemplate(intent);
                    ModelSelection model = chatModelRouter.selectModel(intent, null);

                    Map<String, Object> executionInput = buildExecutionInput(request, intent, executionId);
                    executionInput.put("model_id", model.getModelId());
                    executionInput.put("complexity", intent.getComplexity());

                    return dynamicGraphBuilder.compileAndExecute(templateId, executionInput);
                })
                .flatMapMany(result -> {
                    // 将最终结果转为 Token 流（简化处理：直接输出整个结果文本）
                    String text = result != null ? result.toString() : "No result";
                    return Flux.fromArray(text.split("(?<=\\G.{1})")); // 逐字符发送
                })
                .onErrorResume(e -> Flux.just("❌ 执行失败: " + e.getMessage()));
    }

    // ==================== 辅助方法 ====================
    private CommanderResponse buildSuccessResponse(String executionId,
                                                    Map<String, Object> result,
                                                    String templateId,
                                                    ModelSelection model,
                                                    IntentAnalysis intent,
                                                    long durationMs) {
        return CommanderResponse.success(
                executionId, result,
                CommanderResponse.ArchitectureInfo.builder()
                        .architectureId(templateId)
                        .architectureType("GRAPH_DYNAMIC")
                        .selectionReason("Template: " + templateId)
                        .build(),
                CommanderResponse.ModelInfo.builder()
                        .modelId(model.getModelId())
                        .modelName(model.getModelName())
                        .provider(model.getProvider())
                        .selectionStrategy(model.getSelectionStrategy())
                        .build(),
                intent.getScenario(),
                intent.getComplexity(),
                durationMs
        );
    }

    private Map<String, Object> buildExecutionInput(
            CommanderRequest request, IntentAnalysis intent, String executionId) {
        Map<String, Object> input = new ConcurrentHashMap<>();
        input.put("query", request.getUserInput());
        input.put("execution_id", executionId);
        input.put("session_id", request.getSessionId());
        input.put("scenario", intent.getScenario());
        input.put("complexity", intent.getComplexity());
        input.put("modality", intent.getModality());

        if (request.getContext() != null) {
            input.putAll(request.getContext());
        }
        if (request.getImageBase64() != null) input.put("image_base64", request.getImageBase64());
        if (request.getAudioBase64() != null) input.put("audio_base64", request.getAudioBase64());
        if (request.getVideoUrl() != null) input.put("video_url", request.getVideoUrl());
        return input;
    }

    private void pushProgress(FluxSink<CommanderResponse> sink, String executionId, String stage) {
        CommanderResponse progress = CommanderResponse.builder()
                .executionId(executionId)
                .success(false)
                .fallback(false)
                .stage(stage)
                .build();
        sink.next(progress);
    }

    private void recordExecution(String executionId, CommanderRequest request,
                                 IntentAnalysis intent, String templateId, ModelSelection model,
                                 long durationMs, boolean success, boolean fallback) {
        ExecutionRecord record = ExecutionRecord.builder()
                .executionId(executionId)
                .sessionId(request.getSessionId())
                .timestamp(System.currentTimeMillis())
                .scenario(intent != null ? intent.getScenario() : "UNKNOWN")
                .complexity(intent != null ? intent.getComplexity() : "UNKNOWN")
                .architectureId(templateId != null ? templateId : "fallback")
                .modelId(model != null ? model.getModelId() : "fallback")
                .modality(intent != null ? intent.getModality() : "TEXT")
                .durationMs(durationMs)
                .success(success)
                .fallback(fallback)
                .build();
        executionHistory.put(executionId, record);
        if (executionHistory.size() > 10000) {
            executionHistory.entrySet().removeIf(entry ->
                    System.currentTimeMillis() - entry.getValue().getTimestamp() > 3600000);
        }
    }

    public ExecutionRecord getExecutionHistory(String executionId) {
        return executionHistory.get(executionId);
    }

    public static class CommanderExecutionException extends RuntimeException {
        public CommanderExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}