package com.air.commander.orchestrator;

import com.air.commander.a2a.BaseNacosA2ARouter;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import com.air.commander.model.Step;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动态编排器
 * 用于执行步骤计划
 */
@Component
public class DynamicOrchestrator {

    private final ChatClient chatClient;
    private final BaseNacosA2ARouter baseNacosA2ARouter;
    private final PlanValidator planValidator;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;

    public DynamicOrchestrator(@Qualifier("reasoningModelClient") ChatClient reasoningModelClient,
                               BaseNacosA2ARouter baseNacosA2ARouter,
                               PlanValidator planValidator,
                               ResilienceManager resilienceManager) {
        this.chatClient = reasoningModelClient;
        this.baseNacosA2ARouter = baseNacosA2ARouter;
        this.planValidator = planValidator;
        this.resilienceManager = resilienceManager;
        this.objectMapper = new ObjectMapper();
    }


    public OrchestrationPlan generatePlan(String userInput, MemoryContext memoryCtx) {
        List<String> agents = new ArrayList<>(baseNacosA2ARouter.getAvailableAgents());
        String prompt = buildPrompt(userInput, memoryCtx, agents);
        String llmOutput = resilienceManager.executeWithFullProtection(
                "llm-reasoning-model",
                () -> chatClient.prompt(prompt).call().content(),
                () -> "{\"steps\": [{\"id\":\"step1\",\"type\":\"LLM_CALL\",\"task\":\"ANSWER\"}]}"
        );
        try {
            Map<String, Object> planMap = objectMapper.readValue(llmOutput, Map.class);
            List<Step> steps = parseSteps(planMap);
            OrchestrationPlan plan = OrchestrationPlan.builder()
                    .planId(UUID.randomUUID().toString())
                    .executionMode(detectMode(planMap))
                    .steps(steps)
                    .build();
            if (!planValidator.validate(plan).isValid()) {
                // 重试一次
                llmOutput = chatClient.prompt(prompt + "\n请修正错误").call().content();
                planMap = objectMapper.readValue(llmOutput, Map.class);
                steps = parseSteps(planMap);
                plan.setSteps(steps);
            }
            return plan;
        } catch (Exception e) {
            throw new RuntimeException("Plan generation failed", e);
        }
    }

    private List<Step> parseSteps(Map<String, Object> planMap) {
        List<Map<String, Object>> stepList = (List<Map<String, Object>>) planMap.get("steps");
        List<Step> steps = new ArrayList<>();
        for (Map<String, Object> s : stepList) {
            steps.add(Step.builder()
                    .id((String) s.get("id"))
                    .type(Step.StepType.valueOf((String) s.get("type")))
                    .agent((String) s.get("agent"))
                    .task((String) s.get("task"))
                    .input((Map<String, Object>) s.get("input"))
                    .dependsOn((List<String>) s.get("dependsOn"))
                    .build());
        }
        return steps;
    }

    private OrchestrationPlan.ExecutionMode detectMode(Map<String, Object> planMap) {
        try {
            return OrchestrationPlan.ExecutionMode.valueOf(
                    (String) planMap.getOrDefault("executionMode", "SEQUENTIAL"));
        } catch (Exception e) {
            return OrchestrationPlan.ExecutionMode.SEQUENTIAL;
        }
    }

    private String buildPrompt(String userInput, MemoryContext ctx, List<String> agents) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能任务编排专家。请根据用户需求，结合提供的上下文信息，生成一个JSON格式的执行计划。\n\n");

        // 1. 可用 Agent 列表
        sb.append("=== 可用Agent ===\n");
        for (String agent : agents) {
            sb.append("- ").append(agent).append("\n");
        }
        sb.append("\n");

        // 2. 用户画像
        if (ctx.getUserProfile() != null && !ctx.getUserProfile().isEmpty()) {
            sb.append("=== 用户画像 ===\n");
            sb.append(ctx.getUserProfile()).append("\n\n");
        }

        // 3. 用户偏好
        if (ctx.getPreferences() != null && !ctx.getPreferences().isEmpty()) {
            sb.append("=== 用户偏好 ===\n");
            sb.append(ctx.getPreferences()).append("\n\n");
        }

        // 4. 最近对话（取最近10条，帮助理解上下文连续性）
        if (ctx.getRecentMessages() != null && !ctx.getRecentMessages().isEmpty()) {
            sb.append("=== 最近对话 ===\n");
            ctx.getRecentMessages().stream()
                    .skip(Math.max(0, ctx.getRecentMessages().size() - 10))
                    .forEach(msg -> sb.append("- ").append(msg.get("role"))
                            .append(": ").append(msg.get("content")).append("\n"));
            sb.append("\n");
        }

        // 5. 相似历史案例（Few-shot）
        if (ctx.getSimilarCases() != null && !ctx.getSimilarCases().isEmpty()) {
            sb.append("=== 参考成功案例 ===\n");
            int count = Math.min(3, ctx.getSimilarCases().size());
            for (int i = 0; i < count; i++) {
                sb.append("案例").append(i + 1).append(": ")
                        .append(ctx.getSimilarCases().get(i)).append("\n");
            }
            sb.append("\n");
        }

        // 6. 当前用户请求
        sb.append("=== 当前用户请求 ===\n");
        sb.append(userInput).append("\n\n");

        // 7. 输出格式约束
        sb.append("请根据以上信息，生成最优的执行计划，输出严格符合以下JSON格式（不要包含其他内容）：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL|PARALLEL|CONDITIONAL|ITERATIVE_CORRECTION|COMPETITIVE|PIPELINE\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"A2A_DELEGATE|LLM_CALL|INTERRUPT\",\n");
        sb.append("      \"agent\": \"agent名称（type=A2A_DELEGATE时必填）\",\n");
        sb.append("      \"task\": \"任务描述\",\n");
        sb.append("      \"input\": {\"key\": \"value或{stepX.output}引用\"},\n");
        sb.append("      \"dependsOn\": [\"前置步骤id\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}");

        return sb.toString();
    }
}