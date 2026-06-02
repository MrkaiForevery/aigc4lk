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

import java.util.List;
import java.util.Map;

/**
 * Commander Controller
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
     * 阶段级流式执行 (SSE)
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