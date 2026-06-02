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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommanderAgent {

    private final IntentClassifier intentClassifier;
    private final ChatModelRouter chatModelRouter;
    private final GraphTemplateSelector templateSelector;
    private final DynamicGraphBuilder dynamicGraphBuilder;

    private final Map<String, ExecutionRecord> executionHistory = new ConcurrentHashMap<>();

    // ==================== 流式执行 ====================
    @CircuitBreaker(name = "architecture-execution", fallbackMethod = "fallbackExecute")
    @Retry(name = "commander-retry")
    public Flux<CommanderResponse> executeStream(CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        String userId = extractUserId(request);
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
        String threadId = userId + "::" + sessionId;

        // 同步：意图识别、模板选择、模型选择
        IntentAnalysis intent = intentClassifier.analyzeIntent(request.getUserInput(), sessionId, userId);
        String templateId = templateSelector.selectTemplate(intent);
        ModelSelection model = chatModelRouter.selectModel(intent);

        Map<String, Object> executionInput = buildExecutionInput(request, intent, executionId);
        executionInput.put("model_id", model.getModelId());
        executionInput.put("complexity", intent.getComplexity());
        executionInput.put("template_id", templateId);
        executionInput.put("thread_id", threadId);
        executionInput.put("user_id", userId);
        executionInput.put("session_id", sessionId);

        // 进度事件
        Flux<CommanderResponse> progressFlux = Flux.just(
                createProgress(executionId, "INTENT_COMPLETED", Map.of("scenario", intent.getScenario())),
                createProgress(executionId, "TEMPLATE_SELECTED", Map.of("templateId", templateId)),
                createProgress(executionId, "EXECUTING", Map.of("message", "正在生成内容..."))
        );

        AtomicReference<String> fullDoc = new AtomicReference<>("");

        Flux<CommanderResponse> contentFlux = dynamicGraphBuilder
                .compileAndExecuteStream(templateId, executionInput)
                .map(fragment -> {
                    // 累积文档
                    fullDoc.updateAndGet(current -> current + fragment);
                    return createDataProgress(executionId, "GENERATING", fragment);
                })
                .concatWith(Flux.defer(() -> {
                    long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                    CommanderResponse finalResponse = buildSuccessResponse(
                            executionId, fullDoc.get(), templateId, model, intent, durationMs);
                    return Flux.just(finalResponse);
                }));

        return Flux.concat(progressFlux, contentFlux)
                .doOnComplete(() -> log.info("Stream completed"))
                .doOnError(e -> log.error("Stream error", e));
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

    // 重载 buildSuccessResponse，支持直接传入文档字符串
    private CommanderResponse buildSuccessResponse(String executionId, String document,
                                                   String templateId, ModelSelection model,
                                                   IntentAnalysis intent, long durationMs) {
        Map<String, Object> resultMap = Map.of("document", document);
        return buildSuccessResponse(executionId, resultMap, templateId, model, intent, durationMs);
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

    public ExecutionRecord getExecutionHistory(String executionId) {
        return executionHistory.get(executionId);
    }

    private String extractUserId(CommanderRequest request) {
        return Optional.ofNullable(request.getContext())
                .map(ctx -> ctx.get("userId"))
                .map(Object::toString)
                .orElse("anonymous");
    }

    private CommanderResponse createProgress(String executionId, String stage, Map<String, Object> data) {
        return CommanderResponse.builder()
                .executionId(executionId)
                .success(false)          // 进度事件标记为未完成
                .fallback(false)
                .stage(stage)            // 当前阶段标识
                .result(data)            // 携带阶段描述数据（如 scenario、templateId）
                .build();
    }

    private CommanderResponse createDataProgress(String executionId, String stage, String fragment) {
        return CommanderResponse.builder()
                .executionId(executionId)
                .success(false)
                .fallback(false)
                .stage(stage)
                .result(Map.of("fragment", fragment))   // 携带文档片段
                .build();
    }

}