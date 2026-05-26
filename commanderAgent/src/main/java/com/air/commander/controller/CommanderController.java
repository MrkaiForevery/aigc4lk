package com.air.commander.controller;

import com.air.commander.agent.CommanderAgent;
import com.air.commander.entity.CommanderRequest;
import com.air.commander.entity.ExecutionRecord;
import com.air.commander.model.ChatModelRouter;
import com.air.platform.common.model.ModelDefinition;
import com.air.platform.common.tranfer.CommanderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Commander Controller
 * 
 * 提供 Commander Agent 的 HTTP API：
 * - 同步执行
 * - 异步执行
 * - 阶段级流式输出
 * - Token 级流式输出
 * - 执行历史查询
 * - 系统信息查询
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/commander")
@RequiredArgsConstructor
public class CommanderController {

    private final CommanderAgent commanderAgent;
    private final ChatModelRouter chatModelRouter;

    // ==================== 执行接口 ====================

    /**
     * 同步执行
     * 
     * 请求体示例：
     * {
     *   "userInput": "分析当前AI行业发展趋势",
     *   "sessionId": "session-001",
     *   "context": { "userId": "u123" }
     * }
     * 
     * 响应：完整的 CommanderResponse（包含架构信息、模型信息、耗时等）
     */
    @PostMapping("/execute")
    public ResponseEntity<CommanderResponse> execute(@RequestBody CommanderRequest request) {
        log.info("📥 [HTTP] Sync execute request, session: {}", request.getSessionId());
        CommanderResponse response = commanderAgent.execute(request);
        log.info("📤 [HTTP] Sync execute completed: {}", response.getExecutionId());
        return ResponseEntity.ok(response);
    }

    /**
     * 异步执行（立即返回执行ID，后台执行）
     * 
     * 响应：{ "executionId": "xxx", "status": "processing" }
     * 后续通过 /history/{executionId} 查询结果
     */
    @PostMapping("/execute/async")
    public ResponseEntity<Map<String, Object>> executeAsync(@RequestBody CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();
        log.info("📥 [HTTP] Async execute request, executionId: {}", executionId);

        // 提交异步任务
        commanderAgent.executeAsync(request)
                .thenAccept(response -> log.info("📤 [Async] Task completed: {}", response.getExecutionId()))
                .exceptionally(throwable -> {
                    log.error("❌ [Async] Task failed: {}", executionId, throwable);
                    return null;
                });

        return ResponseEntity.accepted().body(Map.of(
                "execution_id", executionId,
                "status", "processing",
                "message", "Task submitted successfully. Check status via GET /history/" + executionId
        ));
    }

    /**
     * 阶段级流式执行 (SSE)
     * 
     * 推送两种事件：
     * 1. event: progress  → CommanderResponse（stage 有值，success=false）
     * 2. event: result    → CommanderResponse（stage 为 null，success=true）
     * 
     * 客户端示例：
     * eventSource.addEventListener("progress", (e) => { showProgress(JSON.parse(e.data)); });
     * eventSource.addEventListener("result",  (e) => { showResult(JSON.parse(e.data)); });
     */
    @PostMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CommanderResponse>> executeStream(@RequestBody CommanderRequest request) {
        log.info("📥 [HTTP] Stream execute request");

        return commanderAgent.executeStream(request)
                .map(response -> {
                    if (response.isProgress()) {
                        return ServerSentEvent.<CommanderResponse>builder()
                                .id(response.getExecutionId())
                                .event("progress")
                                .data(response)
                                .build();
                    } else {
                        return ServerSentEvent.<CommanderResponse>builder()
                                .id(response.getExecutionId())
                                .event("result")
                                .data(response)
                                .build();
                    }
                })
                .doOnComplete(() -> log.info("📤 [HTTP] Stream execute completed"))
                .doOnError(e -> log.error("❌ [HTTP] Stream execute failed", e));
    }

    /**
     * Token 级流式执行 (SSE)
     * 
     * 推送两种事件：
     * 1. event: metadata → 执行元信息（执行ID、模型名等）
     * 2. event: token    → 模型生成的文本片段
     * 
     * 客户端示例：
     * eventSource.addEventListener("metadata", (e) => { showMeta(JSON.parse(e.data)); });
     * eventSource.addEventListener("token",    (e) => { appendText(e.data); });
     */
    @PostMapping(value = "/execute/stream/token", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> executeTokenStream(@RequestBody CommanderRequest request) {
        String executionId = UUID.randomUUID().toString();
        log.info("📥 [HTTP] Token stream request, executionId: {}", executionId);

        // 先发送元信息
        ServerSentEvent<String> metadata = ServerSentEvent.<String>builder()
                .id(executionId)
                .event("metadata")
                .data("{\"executionId\":\"" + executionId + "\",\"timestamp\":" + System.currentTimeMillis() + "}")
                .build();

        // 再发送 token 流
        Flux<ServerSentEvent<String>> tokens = commanderAgent.executeTokenStream(request)
                .map(token -> ServerSentEvent.<String>builder()
                        .id(executionId)
                        .event("token")
                        .data(token)
                        .build());

        return Flux.concat(
                Mono.just(metadata),
                tokens
        ).doOnComplete(() -> log.info("📤 [HTTP] Token stream completed: {}", executionId))
         .doOnError(e -> log.error("❌ [HTTP] Token stream failed: {}", executionId, e));
    }

    // ==================== 查询接口 ====================

    /**
     * 查询执行历史
     */
    @GetMapping("/history/{executionId}")
    public ResponseEntity<ExecutionRecord> getExecutionHistory(@PathVariable String executionId) {
        log.debug("📥 [HTTP] Query execution history: {}", executionId);
        ExecutionRecord record = commanderAgent.getExecutionHistory(executionId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }

    /**
     * 获取可用架构列表
     */
    @GetMapping("/architectures")
    public ResponseEntity<List<Map<String, String>>> getArchitectures() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "id", "sequential-pipeline",
                        "type", "SEQUENTIAL",
                        "description", "顺序流水线 - 适用于文档生成、代码审查等线性任务"
                ),
                Map.of(
                        "id", "parallel-analysis",
                        "type", "PARALLEL",
                        "description", "并行分析 - 适用于多维度数据分析"
                ),
                Map.of(
                        "id", "smart-routing",
                        "type", "LLM_ROUTING",
                        "description", "智能路由 - 适用于客服工单分发"
                ),
                Map.of(
                        "id", "debate-system",
                        "type", "CUSTOM_GRAPH",
                        "description", "辩论系统 - 适用于投资决策等需要多视角的任务"
                )
        ));
    }

    /**
     * 获取可用模型列表
     */
    @GetMapping("/models")
    public ResponseEntity<List<ModelDefinition>> getModels() {
        return ResponseEntity.ok(chatModelRouter.getAvailableModels());
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "commander-service",
                "version", "1.0.0",
                "timestamp", System.currentTimeMillis()
        ));
    }
}