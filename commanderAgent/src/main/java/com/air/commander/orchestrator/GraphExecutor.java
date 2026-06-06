package com.air.commander.orchestrator;

import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 流程图的具体实施执行器
 */
@Slf4j
@Component
public class GraphExecutor {

    private final BaseNacosA2ARouter a2aRouter;
    private final ChatClient easyChatClient;
    private final InterruptHandler interruptHandler;
    private final GraphBuilder graphBuilder;
    private final ResilienceManager resilience;

    private final ObjectMapper objectMapper;

    // 固定线程池，替代虚拟线程
    private final ExecutorService parallelExecutor;


    public GraphExecutor(BaseNacosA2ARouter a2aRouter,
                         @Qualifier("fastModelClient") ChatClient easyChatClient,
                         InterruptHandler interruptHandler,
                         GraphBuilder graphBuilder,
                         ResilienceManager resilience,
                         ObjectMapper objectMapper) {
        this.a2aRouter = a2aRouter;
        this.easyChatClient = easyChatClient;
        this.interruptHandler = interruptHandler;
        this.graphBuilder = graphBuilder;
        this.resilience = resilience;
        this.objectMapper = objectMapper;
        this.parallelExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors());
    }


    public List<ExecutionResult> execute(OrchestrationPlan plan,
                                         String threadId,
                                         String userId,
                                         Map<String, String> tokens,
                                         String xid,
                                         MemoryContext memoryCtx) {
        return switch (plan.getExecutionMode()) {
            case SEQUENTIAL -> executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
            case PARALLEL -> executeParallel(plan, threadId, userId, tokens, xid, memoryCtx);
            case CONDITIONAL -> executeConditional(plan, threadId, userId, tokens, xid, memoryCtx);
            case ITERATIVE_CORRECTION -> executeIterative(plan, threadId, userId, tokens, xid, memoryCtx);
            case COMPETITIVE -> executeCompetitive(plan, threadId, userId, tokens, xid, memoryCtx);
            case PIPELINE -> executePipeline(plan, threadId, userId, tokens, xid, memoryCtx);
        };
    }

    private List<ExecutionResult> executeSequential(OrchestrationPlan plan,
                                                    String threadId,
                                                    String userId,
                                                    Map<String, String> tokens,
                                                    String xid,
                                                    MemoryContext memoryCtx) {
        List<Step> ordered = graphBuilder.buildExecutionOrder(plan);
        Map<String, Object> context = new ConcurrentHashMap<>();
        List<ExecutionResult> results = new ArrayList<>();
        for (Step step : ordered) {
            ExecutionResult r = executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx);
            results.add(r);
            if (r.getCommand() != null) break;
            if (r.isSuccess()) context.put(step.getId() + ".output", r.getOutput());
            if (!r.isSuccess() && step.isMandatory()) break;
        }
        return results;
    }

    private List<ExecutionResult> executeParallel(OrchestrationPlan plan,
                                                  String threadId,
                                                  String userId,
                                                  Map<String, String> tokens,
                                                  String xid,
                                                  MemoryContext memoryCtx) {
        List<Step> steps = plan.getSteps();
        Map<String, Object> context = new ConcurrentHashMap<>();
        // 无依赖的步骤并行执行
        List<Step> parallelSteps = steps.stream()
                .filter(s -> s.getDependsOn() == null || s.getDependsOn().isEmpty())
                .collect(Collectors.toList());

        List<CompletableFuture<ExecutionResult>> futures = parallelSteps.stream()
                .map(step -> CompletableFuture.supplyAsync(() ->
                                executeSingleStep(step, context, threadId, userId, tokens, xid, memoryCtx),
                        parallelExecutor))
                .collect(Collectors.toList());

        List<ExecutionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        results.forEach(r -> {
            if (r.isSuccess()) context.put(r.getStepId() + ".output", r.getOutput());
        });

        // 注：这里仅实现了一轮并行，实际生产需循环处理依赖满足的后续步骤，此处从简
        return results;
    }

    private List<ExecutionResult> executeConditional(OrchestrationPlan plan,
                                                     String threadId,
                                                     String userId,
                                                     Map<String, String> tokens,
                                                     String xid,
                                                     MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展条件分支逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private List<ExecutionResult> executeIterative(OrchestrationPlan plan,
                                                   String threadId,
                                                   String userId,
                                                   Map<String, String> tokens,
                                                   String xid,
                                                   MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展循环纠正逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private List<ExecutionResult> executeCompetitive(OrchestrationPlan plan,
                                                     String threadId,
                                                     String userId,
                                                     Map<String, String> tokens,
                                                     String xid,
                                                     MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展竞争选择逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private List<ExecutionResult> executePipeline(OrchestrationPlan plan,
                                                  String threadId,
                                                  String userId,
                                                  Map<String, String> tokens,
                                                  String xid,
                                                  MemoryContext memoryCtx) {
        // 暂用顺序执行代替，后续可扩展流水线传递逻辑
        return executeSequential(plan, threadId, userId, tokens, xid, memoryCtx);
    }

    private ExecutionResult executeSingleStep(Step step,
                                              Map<String, Object> context,
                                              String threadId,
                                              String userId,
                                              Map<String, String> tokens,
                                              String xid,
                                              MemoryContext memoryCtx) {
        return switch (step.getType()) {
            case A2A_DELEGATE -> a2aRouter.callAgent(step, context, tokens, threadId, xid, memoryCtx);
            case LLM_CALL -> {
                try {
                    // 1. 构建 Prompt：使用 step 中的 task 或 input
                    String prompt = buildLLMPrompt(step, context, memoryCtx);

                    // 2. 调用模型（带弹性保护）
                    String llmOutput = resilience.executeWithFullProtection(
                            "llm-step-call",
                            () -> easyChatClient.prompt(prompt).call().content(),
                            () -> "LLM调用降级，返回默认回复"
                    );

                    // 3. 返回结果
                    yield ExecutionResult.builder()
                            .stepId(step.getId())
                            .success(true)
                            .output(Map.of("content", llmOutput))
                            .build();
                } catch (Exception e) {
                    log.error("LLM步骤执行失败: stepId={}", step.getId(), e);
                    yield ExecutionResult.builder()
                            .stepId(step.getId())
                            .success(false)
                            .error("LLM调用失败: " + e.getMessage())
                            .build();
                }
            }
            case INTERRUPT -> ExecutionResult.builder()
                    .stepId(step.getId())
                    .success(false)
                    .command(ExecutionResult.Command.builder()
                            .type("REQUEST_CONFIRM")
                            .message(step.getQuestion())
                            .requiredScopes(List.of())
                            .build())
                    .build();
        };
    }

    /**
     * 根据 Step 和上下文构建 LLM 的 Prompt
     */
    private String buildLLMPrompt(Step step, Map<String, Object> context, MemoryContext memoryCtx) {
        StringBuilder sb = new StringBuilder();

        // ========= 1. 任务描述 =========
        sb.append("【任务】\n");
        sb.append(step.getTask()).append("\n\n");

        // ========= 2. 输入数据（已解析占位符） =========
        if (step.getInput() != null && !step.getInput().isEmpty()) {
            Map<String, Object> resolvedInput = resolveInput(step.getInput(), context);
            sb.append("【输入数据】\n");
            // 如果包含 userQuery，单独高亮展示
            if (resolvedInput.containsKey("userQuery")) {
                sb.append("用户原始请求：\n").append(resolvedInput.get("userQuery")).append("\n");
            }
            // 其他参数格式化输出
            resolvedInput.forEach((key, value) -> {
                if (!"userQuery".equals(key)) {
                    sb.append(key).append(": ").append(formatValue(value)).append("\n");
                }
            });
            sb.append("\n");
        }

        // ========= 3. 对话历史（只取最近 3 条，过滤占位符） =========
        if (memoryCtx.getRecentMessages() != null && !memoryCtx.getRecentMessages().isEmpty()) {
            String recent = memoryCtx.getRecentMessages().stream()
                    .filter(msg -> !"done".equals(msg.getContent()))   // 过滤无效消息
                    .skip(Math.max(0, memoryCtx.getRecentMessages().size() - 3))
                    .map(msg -> msg.getRole() + ": " + msg.getContent())
                    .collect(Collectors.joining("\n"));
            if (!recent.isEmpty()) {
                sb.append("【对话历史】\n").append(recent).append("\n\n");
            }
        }

        // ========= 4. 用户偏好 =========todo 先不注入这段提示词，污染大模型判断
//        if (memoryCtx.getPreferences() != null && !memoryCtx.getPreferences().isEmpty()) {
//            sb.append("【用户偏好】\n");
//            memoryCtx.getPreferences().forEach((key, value) ->
//                    sb.append("- ").append(key).append(": ").append(value).append("\n"));
//            sb.append("\n");
//        }

        // ========= 5. 相关知识（可选） ========= todo 先不注入这段提示词，污染大模型判断
//        if (memoryCtx.getKnowledgeChunks() != null && !memoryCtx.getKnowledgeChunks().isEmpty()) {
//            sb.append("【相关知识】\n");
//            memoryCtx.getKnowledgeChunks().forEach(chunk -> sb.append(chunk).append("\n"));
//            sb.append("\n");
//        }

        return sb.toString();
    }

    // 辅助方法：格式化值，避免直接调用 toString 导致不可读
    private String formatValue(Object value) {
        if (value == null) return "null";
        // 如果是简单类型或字符串，直接返回
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        // 如果是集合或 Map，序列化为 JSON 字符串（需要 ObjectMapper）
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    /**
     * 解析输入参数中的变量引用，例如 {step1.output} -> 上下文中的实际对象
     */
    private Map<String, Object> resolveInput(Map<String, Object> input, Map<String, Object> context) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), context));
        }
        return resolved;
    }

    /**
     * 递归解析单个值中的占位符
     */
    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, Map<String, Object> context) {
        if (value instanceof String str) {
            // 完全匹配 {xxx} ：直接返回上下文中的原始对象
            if (str.matches("\\{[^}]+\\}")) {
                String refKey = str.substring(1, str.length() - 1);
                Object ctxValue = context.get(refKey);
                if (ctxValue != null) {
                    return ctxValue;   // 保持原类型（Map、List等）
                } else {
                    log.warn("无法解析占位符引用: {}，上下文无此键", refKey);
                    return str;
                }
            }
            // 部分包含占位符：进行字符串替换
            else if (str.contains("{")) {
                return replacePlaceholders(str, context);
            }
            // 普通字符串
            return str;

        } else if (value instanceof Map) {
            // 递归处理 Map 内的值
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> resolvedMap = new HashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                resolvedMap.put(e.getKey(), resolveValue(e.getValue(), context));
            }
            return resolvedMap;

        } else if (value instanceof List) {
            // 递归处理 List 内的元素
            List<Object> list = (List<Object>) value;
            return list.stream()
                    .map(item -> resolveValue(item, context))
                    .collect(Collectors.toList());
        }
        // 其他类型（数字、布尔等）直接返回
        return value;
    }


    /**
     * 替换字符串中所有 {key} 占位符为上下文中的字符串值
     */
    private String replacePlaceholders(String template, Map<String, Object> context) {
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String refKey = matcher.group(1);
            Object ctxValue = context.get(refKey);
            String replacement;
            if (ctxValue != null) {
                replacement = ctxValue.toString();   // 转为字符串嵌入
            } else {
                log.warn("占位符引用缺失: {}", refKey);
                replacement = matcher.group(0);      // 保留原占位符
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}