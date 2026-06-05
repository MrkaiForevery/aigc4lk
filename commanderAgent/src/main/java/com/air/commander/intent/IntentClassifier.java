package com.air.commander.intent;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.air.commander.model.IntentResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.resilience.ResilienceManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class IntentClassifier {

    private final RemoteConfigLoader configLoader;
    private final ChatClient chatClient;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;

    public IntentClassifier(RemoteConfigLoader configLoader,
                            @Qualifier("fastModelClient") ChatClient fastchatClient,
                            ResilienceManager resilienceManage
    ) {
        this.configLoader = configLoader;
        this.chatClient = fastchatClient;
        this.resilienceManager = resilienceManage;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 对用户输入进行意图分类
     * @param userInput    原始用户输入
     * @param memoryContext 当前会话的记忆上下文（已通过 threadId 隔离）
     * @return 意图结果，包含是否匹配模板、场景标识、模板ID、复杂度
     */
    public IntentResult classify(String userInput, MemoryContext memoryContext) {
        // 1. 硬规则匹配（基于可热更新的规则配置）
        for (RemoteConfigLoader.IntentRule rule : configLoader.getIntentRules()) {
            if (rule.matches(userInput)) {
                String templateId = configLoader.getScenarioToTemplateId().get(rule.scenario());
                if (templateId != null) {
                    log.debug("硬规则匹配成功: scenario={}, templateId={}", rule.scenario(), templateId);
                    return new IntentResult(true, rule.scenario(), templateId, rule.complexity());
                }
            }
        }

        // 2. 硬规则未匹配，使用轻量级 LLM 分类，并注入记忆上下文
        log.debug("硬规则未匹配，启动 LLM 意图分类");
        String prompt = buildClassificationPrompt(userInput, memoryContext);
        String llmOutput = resilienceManager.executeWithFullProtection(
                "llm-fast-model",
                () -> chatClient.prompt(prompt).call().content(),
                () -> fallbackLlmOutput()
        );

        // 3. 解析 LLM 返回的 JSON
        try {
            Map<String, Object> responseMap = objectMapper.readValue(llmOutput, Map.class);
            boolean predefined = Boolean.TRUE.equals(responseMap.get("predefined"));
            int complexity = responseMap.containsKey("complexity") ?
                    ((Number) responseMap.get("complexity")).intValue() : 3;

            if (predefined && responseMap.containsKey("scenario")) {
                String scenario = (String) responseMap.get("scenario");
                String templateId = configLoader.getScenarioToTemplateId().get(scenario);
                if (templateId != null) {
                    log.info("LLM 匹配到预定义场景: scenario={}, templateId={}", scenario, templateId);
                    return new IntentResult(true, scenario, templateId, complexity);
                }
            }
            // 未匹配到预定义场景，走动态编排
            log.info("LLM 判断为非预定义场景，使用动态编排, complexity={}", complexity);
            return new IntentResult(false, null, null, complexity);

        } catch (JsonProcessingException e) {
            log.error("解析 LLM 意图分类结果失败, 降级为动态编排", e);
            return new IntentResult(false, null, null, 3);
        }
    }

    /**
     * 构建包含记忆上下文的分类 Prompt
     */
    private String buildClassificationPrompt(String userInput, MemoryContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个意图分类助手。请根据以下信息判断用户请求属于哪个预定义场景。\n\n");

        // 可用的预定义场景列表（从配置中动态获取）
        sb.append("=== 预定义场景列表 ===\n");
        configLoader.getScenarioToTemplateId().keySet().forEach(scenario ->
                sb.append("- ").append(scenario).append("\n")
        );
        sb.append("\n");

        // 用户画像
        if (ctx.getUserProfile() != null && !ctx.getUserProfile().isEmpty()) {
            sb.append("=== 用户画像 ===\n");
            sb.append(ctx.getUserProfile()).append("\n\n");
        }

        // 用户偏好
        if (ctx.getPreferences() != null && !ctx.getPreferences().isEmpty()) {
            sb.append("=== 用户偏好 ===\n");
            sb.append(ctx.getPreferences()).append("\n\n");
        }

        // 最近对话（仅取最近10条，既提供上下文又避免 token 过长）
        if (ctx.getRecentMessages() != null && !ctx.getRecentMessages().isEmpty()) {
            sb.append("=== 最近对话 ===\n");
            ctx.getRecentMessages().stream()
                    .skip(Math.max(0, ctx.getRecentMessages().size() - 10))
                    .forEach(msg -> sb.append("- ").append(msg.get("role"))
                            .append(": ").append(msg.get("content")).append("\n"));
            sb.append("\n");
        }

        // 当前请求
        sb.append("=== 当前用户请求 ===\n");
        sb.append(userInput).append("\n\n");

        // 输出格式要求
        sb.append("请以 JSON 格式返回（不要包含其他内容）：\n");
        sb.append("{ \"predefined\": true/false, \"scenario\": \"场景标识（仅predefined=true时必填）\", \"complexity\": 1-5 }");
        return sb.toString();
    }

    /**
     * LLM 调用失败时的兜底输出
     */
    private String fallbackLlmOutput() {
        return "{\"predefined\": false, \"complexity\": 3}";
    }
}