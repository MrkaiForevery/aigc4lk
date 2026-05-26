package com.air.commander.agent;

import com.air.commander.architecture.ArchitectureSelector;
import com.air.commander.config.ChatClientConfiguration;
import com.air.commander.entity.*;
import com.air.commander.intent.IntentClassifier;
import com.air.commander.model.ChatModelRouter;
import com.air.platform.common.a2a.channel.CommanderChannel;
import com.air.platform.common.tranfer.CommanderResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CommanderAgent 不需要要通过A2A暴露自己的能力，它只负责调用其他业务agent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommanderAgent {

    /**意图分析注入**/
    private final IntentClassifier intentClassifier;

    /**架构策略注入**/
    private final ArchitectureSelector architectureSelector;

    /**模型配置匹配路由器**/
    private final ChatModelRouter chatModelRouter;

    /**模型本地缓存--->来源于ChatClientConfiguration**/
    private final Map<String, ChatModel> modelCache;

    /**模型客户端配置实例化句柄**/
    private final ChatClientConfiguration chatClientConfiguration;

    /**
     * 执行历史 todo 这里后期要改成外部存储，构建共享的chatMemery时需要
     */
    private final Map<String, ExecutionRecord> executionHistory = new ConcurrentHashMap<>();

    //-------------------------------一次性结果防护处理-------------------------------------//
    /**
     * 主入口：处理用户请求
     */
    @CircuitBreaker(name = "architecture-execution", fallbackMethod = "fallbackExecute")
    @Retry(name = "commander-retry")
    public CommanderResponse execute(CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.info("🚀 [{}] Commander execution started", executionId);

        try {
            // 阶段1：意图识别
            IntentAnalysis intent = intentClassifier.analyzeIntent(request.getUserInput());
            log.info("🎯 [{}] Intent: scenario={}, complexity={}, modality={}",
                    executionId, intent.getScenario(), intent.getComplexity(), intent.getModality());

            // 阶段2：架构选择
            CommanderChannel.ArchitectureSelection architecture = architectureSelector.selectArchitecture(intent);
            log.info("🏗️ [{}] Architecture: {} (reason: {})",
                    executionId, architecture.getArchitectureId(), architecture.getSelectionReason());

            // 阶段3：模型选择
            ModelSelection model = chatModelRouter.selectModel(intent, architecture);
            log.info("🤖 [{}] Model: {} ({})",
                    executionId, model.getModelId(), model.getProvider());

            // 阶段4：构建执行输入
            Map<String, Object> executionInput = buildExecutionInput(request, intent, executionId);

            // 阶段5：执行任务
            Map<String, Object> result = executeTask(executionInput, architecture, model);

            // 阶段6：计算耗时
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();

            // 阶段7：记录执行历史
            recordExecution(executionId, request, intent, architecture, model, durationMs, true, false);

            log.info("✅ [{}] Commander execution completed in {}ms", executionId, durationMs);

            return CommanderResponse.success(
                    executionId, result,
                    CommanderResponse.ArchitectureInfo.builder()
                            .architectureId(architecture.getArchitectureId())
                            .architectureType(architecture.getArchitectureType())
                            .selectionReason(architecture.getSelectionReason())
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

        } catch (Exception e) {
            log.error("❌ [{}] Commander execution failed", executionId, e);
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            recordExecution(executionId, request, null, null, null, durationMs, false, false);
            throw new CommanderExecutionException("Execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * 异步执行
     */
    @Async
    public CompletableFuture<CommanderResponse> executeAsync(CommanderRequest request) {
        return CompletableFuture.completedFuture(execute(request));
    }

    /**
     * 降级执行
     */
    public CommanderResponse fallbackExecute(CommanderRequest request, Throwable t) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        log.warn("🔄 [{}] Fallback execution triggered due to: {}", executionId, t.getMessage());

        try {
            // 使用最简单的架构和最稳定的模型
            CommanderChannel.ArchitectureSelection fallbackArchitecture = CommanderChannel.ArchitectureSelection.builder()
                    .architectureId("sequential-pipeline")
                    .architectureType("SEQUENTIAL")
                    .selectionReason("Fallback降级")
                    .build();

            ModelSelection fallbackModel = ModelSelection.builder()
                    .modelId("qwen-turbo")
                    .modelName("qwen-turbo")
                    .provider("alibaba")
                    .selectionStrategy("fallback")
                    .build();

            // 降级时也通过动态创建获取模型
            ChatModel fallbackChatModel = getOrCreateChatModel(fallbackModel);

            // 构建执行参数
            Map<String, Object> executionInput = new java.util.HashMap<>();
            executionInput.put("query", request.getUserInput());
            executionInput.put("execution_id", executionId + "-fallback");
            executionInput.put("fallback", true);

            // ✅ 关键：将降级模型放入输入中，传递给 executeTask
            executionInput.put("_fallback_chat_model_", fallbackChatModel);

            Map<String, Object> result = executeTask(executionInput, fallbackArchitecture, fallbackModel);

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            log.info("✅ [{}] Fallback execution completed in {}ms", executionId, durationMs);

            return CommanderResponse.fallback(executionId, result, t.getMessage(), durationMs);

        } catch (Exception e) {
            log.error("❌ [{}] Fallback also failed", executionId, e);
            return CommanderResponse.error(executionId,
                    "Primary: " + t.getMessage() + "; Fallback: " + e.getMessage());
        }
    }


    //-------------------------------流式结果结果返回处理-------------------------------------//
    /**
     * 流式执行 - 中间阶段推送进度，最终返回完整的 CommanderResponse
     */
    public Flux<CommanderResponse> executeStream(CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        return Flux.create(sink -> {
            try {
                // 阶段1：意图识别
                pushProgress(sink, executionId, "INTENT_ANALYSIS");
                IntentAnalysis intent = intentClassifier.analyzeIntent(request.getUserInput());

                // 阶段2：架构选择
                pushProgress(sink, executionId, "ARCHITECTURE_SELECTION");
                CommanderChannel.ArchitectureSelection architecture =
                        architectureSelector.selectArchitecture(intent);

                // 阶段3：模型选择
                pushProgress(sink, executionId, "MODEL_SELECTION");
                ModelSelection model = chatModelRouter.selectModel(intent, architecture);

                // 阶段4-5：执行任务
                pushProgress(sink, executionId, "TASK_EXECUTION");
                Map<String, Object> executionInput = buildExecutionInput(request, intent, executionId);
                Map<String, Object> result = executeTask(executionInput, architecture, model);

                // 构建完整的 CommanderResponse（和同步方法一样）
                long durationMs = Duration.between(startTime, Instant.now()).toMillis();

                CommanderResponse response = CommanderResponse.success(
                        executionId, result,
                        CommanderResponse.ArchitectureInfo.builder()
                                .architectureId(architecture.getArchitectureId())
                                .architectureType(architecture.getArchitectureType())
                                .selectionReason(architecture.getSelectionReason())
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

                // 最终推送完整结果
                sink.next(response);
                sink.complete();

            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    /**
     * 推送进度事件（用特殊标记的 CommanderResponse 表示进度）
     */
    private void pushProgress(
            reactor.core.publisher.FluxSink<CommanderResponse> sink,
            String executionId,
            String stage) {

        // 进度事件：success=false 且带有特殊标记
        CommanderResponse progress = CommanderResponse.builder()
                .executionId(executionId)
                .success(false)           // 进度事件标记为"未完成"
                .fallback(false)
                .stage(stage)             // 当前阶段（需要在 CommanderResponse 中加这个字段）
                .build();

        sink.next(progress);
    }


    /**
     * Token 级别的流式输出 - 实时返回模型生成的每个 Token
     */
    public Flux<String> executeTokenStream(CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();

        return Flux.defer(() -> {
            IntentAnalysis intent = intentClassifier.analyzeIntent(request.getUserInput());
            CommanderChannel.ArchitectureSelection architecture = architectureSelector.selectArchitecture(intent);
            ModelSelection model = chatModelRouter.selectModel(intent, architecture);
            // 动态获取模型
            ChatModel selectedModel = getOrCreateChatModel(model);
            ChatClient client = ChatClient.builder(selectedModel).build();

            Map<String, Object> executionInput = buildExecutionInput(request, intent, executionId);

            return Flux.just(
                    "📋 场景: " + intent.getScenario() + "\n",
                    "🏗️ 架构: " + architecture.getArchitectureId() + "\n",
                    "🤖 模型: " + model.getModelName() + "\n\n"
            ).concatWith(
                    // 流式调用模型
                    client.prompt()
                            .user(userMessage -> userMessage
                                    .text("处理以下请求：{query}")
                                    .param("query", executionInput.get("query").toString()))
                            .stream()
                            .content()
            );
        });
    }

    /**
     * 执行具体任务
     */
    private Map<String, Object> executeTask(
            Map<String, Object> input,
            CommanderChannel.ArchitectureSelection architecture,
            ModelSelection model) {

        // 根据架构类型执行不同的逻辑
        return switch (architecture.getArchitectureType()) {
            case "SEQUENTIAL" -> executeSequential(input, model);
            case "PARALLEL" -> executeParallel(input, model);
            case "LLM_ROUTING" -> executeLlmRouting(input, model);
            case "CUSTOM_GRAPH" -> executeCustomGraph(input, model);
            default -> executeDefault(input, model);
        };
    }

    /**
     * 顺序执行
     */
    private Map<String, Object> executeSequential(Map<String, Object> input, ModelSelection model) {
        log.debug("Executing sequential pipeline with model: {}", model.getModelId());

        // 使用 ChatClient 进行顺序处理
        ChatModel selectedModel = getOrCreateChatModel(model);
        ChatClient client = ChatClient.builder(selectedModel).build();

        // 阶段1：分析
        String analysis = client.prompt()
                .user(userMessage -> userMessage
                        .text("分析以下请求：{query}")
                        .param("query", input.get("query").toString()))
                .call()
                .content();

        // 阶段2：处理
        String processed = client.prompt()
                .user(userMessage -> userMessage
                        .text("基于分析结果进行处理：\n分析：{analysis}\n原始请求：{query}")
                        .param("analysis", analysis)  // 参数1
                        .param("query", input.get("query").toString())        // 参数2
                )
                .call()
                .content();

        // 阶段3：生成最终结果
        String finalResult = client.prompt()
                .user(userMessage -> userMessage
                        .text("基于处理结果生成最终输出：\n处理结果：{processed}")
                        .param("processed", processed)
                )
                .call()
                .content();

        return Map.of(
                "analysis", analysis,
                "processed", processed,
                "final_result", finalResult,
                "architecture", "sequential-pipeline",
                "model", model.getModelName()
        );
    }

    /**
     * 并行执行（简化版 - 使用多轮对话模拟）
     */
    private Map<String, Object> executeParallel(Map<String, Object> input, ModelSelection model) {
        log.debug("Executing parallel analysis with model: {}", model.getModelId());

        ChatModel selectedModel = getOrCreateChatModel(model);
        ChatClient client = ChatClient.builder(selectedModel).build();

        // 并行分析多个维度
        String dimension1 = client.prompt()
                .user(userMessage -> userMessage
                        .text("从维度1分析：{query}")
                        .param("query", input.get("query").toString()))
                .call()
                .content();

        String dimension2 = client.prompt()
                .user(userMessage -> userMessage
                        .text("从维度2分析：{query}")
                        .param("query", input.get("query").toString()))
                .call()
                .content();

        String dimension3 = client.prompt()
                .user(userMessage -> userMessage
                        .text("从维度3分析：{query}")
                        .param("query", input.get("query").toString()))
                .call()
                .content();

        // 汇总分析
        String summary = client.prompt()
                .user(userMessage -> userMessage
                        .text("""
                                汇总以下多维度分析结果：
                                维度1：{dim1}
                                维度2：{dim2}
                                维度3：{dim3}
                                """)
                        .param("dim1", dimension1)
                        .param("dim2", dimension2)
                        .param("dim3", dimension3)
                )
                .call()
                .content();

        return Map.of(
                "dimension_1", dimension1,
                "dimension_2", dimension2,
                "dimension_3", dimension3,
                "summary", summary,
                "architecture", "parallel-analysis",
                "model", model.getModelName()
        );
    }

    /**
     * LLM路由执行
     */
    private Map<String, Object> executeLlmRouting(Map<String, Object> input, ModelSelection model) {
        log.debug("Executing LLM routing with model: {}", model.getModelId());

        ChatModel selectedModel = getOrCreateChatModel(model);
        ChatClient client = ChatClient.builder(selectedModel).build();

        // LLM决定路由到哪个处理器
        String routingDecision = client.prompt()
                .user(userMessage -> userMessage
                        .text("""
                                根据以下请求，决定最适合的处理方式。返回JSON：
                                {
                                    "route": "after_sales|tech_support|complaint|general",
                                    "reason": "路由原因"
                                }
                                
                                请求：{query}
                                """)
                        .param("query", input.get("query").toString())
                )
                .call()
                .content();

        // 根据路由执行处理
        String result = client.prompt()
                .user(userMessage -> userMessage
                        .text("根据路由决策 {route} 处理请求：{query}")
                        .param("route", routingDecision)
                        .param("query", input.get("query").toString())
                )
                .call()
                .content();

        return Map.of(
                "routing_decision", routingDecision,
                "result", result,
                "architecture", "llm-routing",
                "model", model.getModelName()
        );
    }

    /**
     * 自定义图执行
     */
    private Map<String, Object> executeCustomGraph(Map<String, Object> input, ModelSelection model) {
        log.debug("Executing custom graph with model: {}", model.getModelId());

        ChatModel selectedModel = getOrCreateChatModel(model);
        ChatClient client = ChatClient.builder(selectedModel).build();

        // 多轮辩论
        StringBuilder debateHistory = new StringBuilder();

        // 第1轮
        String round1 = client.prompt()
                .user(userMessage -> userMessage
                        .text("提出初始观点：{query}")
                        .param("query", input.get("query").toString())
                )
                .call()
                .content();
        debateHistory.append("第1轮观点：").append(round1).append("\n");

        // 第2轮
        String round2 = client.prompt()
                .user(userMessage -> userMessage
                        .text("基于以下观点提出反驳或补充：\n{history}")
                        .param("history", debateHistory.toString())
                )
                .call()
                .content();
        debateHistory.append("第2轮观点：").append(round2).append("\n");

        // 仲裁
        String arbitration = client.prompt()
                .user(userMessage -> userMessage
                        .text("基于以下辩论内容做出最终决策：\n{history}")
                        .param("history", debateHistory.toString())
                )
                .call()
                .content();

        return Map.of(
                "debate_history", debateHistory.toString(),
                "arbitration", arbitration,
                "architecture", "custom-graph",
                "model", model.getModelName()
        );
    }

    /**
     * 默认执行
     */
    private Map<String, Object> executeDefault(Map<String, Object> input, ModelSelection model) {
        log.debug("Executing default pipeline with model: {}", model.getModelId());

        ChatModel selectedModel = getOrCreateChatModel(model);
        ChatClient client = ChatClient.builder(selectedModel).build();

        String result = client.prompt()
                .user(userMessage -> userMessage
                        .text("处理以下请求：{query}")
                        .param("query", input.get("query").toString())
                )
                .call()
                .content();

        return Map.of(
                "result", result,
                "architecture", "default",
                "model", model.getModelName()
        );
    }

    /**
     * 构建执行输入
     */
    private Map<String, Object> buildExecutionInput(
            CommanderRequest request,
            IntentAnalysis intent,
            String executionId) {

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

        if (request.getImageBase64() != null) {
            input.put("image_base64", request.getImageBase64());
        }
        if (request.getAudioBase64() != null) {
            input.put("audio_base64", request.getAudioBase64());
        }
        if (request.getVideoUrl() != null) {
            input.put("video_url", request.getVideoUrl());
        }

        return input;
    }

    /**
     * 记录执行历史
     */
    private void recordExecution(
            String executionId,
            CommanderRequest request,
            IntentAnalysis intent,
            CommanderChannel.ArchitectureSelection architecture,
            ModelSelection model,
            long durationMs,
            boolean success,
            boolean fallback) {

        ExecutionRecord record = ExecutionRecord.builder()
                .executionId(executionId)
                .sessionId(request.getSessionId())
                .timestamp(System.currentTimeMillis())
                .scenario(intent != null ? intent.getScenario() : "UNKNOWN")
                .complexity(intent != null ? intent.getComplexity() : "UNKNOWN")
                .architectureId(architecture != null ? architecture.getArchitectureId() : "fallback")
                .modelId(model != null ? model.getModelId() : "fallback")
                .modality(intent != null ? intent.getModality() : "TEXT")
                .durationMs(durationMs)
                .success(success)
                .fallback(fallback)
                .build();

        executionHistory.put(executionId, record);

        // 限制历史记录大小
        if (executionHistory.size() > 10000) {
            // 清理旧记录
            executionHistory.entrySet().removeIf(entry ->
                    System.currentTimeMillis() - entry.getValue().getTimestamp() > 3600000);
        }
    }

    /**
     * 获取执行历史
     */
    public ExecutionRecord getExecutionHistory(String executionId) {
        return executionHistory.get(executionId);
    }

    /**
     * 自定义异常 todo 这个异常的要提出来
     */
    public static class CommanderExecutionException extends RuntimeException {
        public CommanderExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 根据 ModelSelection 动态创建 ChatModel
     */
    private ChatModel getOrCreateChatModel(ModelSelection model) {
        String modelId = model.getModelId();

        // 1. 先从缓存中获取
        ChatModel chatModel = modelCache.get(modelId);
        if (chatModel != null) {
            return chatModel;
        }

        // 2. 缓存中没有，则通过 ChatClientConfiguration 创建
        log.info("Creating new ChatModel for: {}", modelId);
        chatModel = chatClientConfiguration.createModel(modelId);

        // 3. 放入缓存
        modelCache.put(modelId, chatModel);

        return chatModel;
    }
}