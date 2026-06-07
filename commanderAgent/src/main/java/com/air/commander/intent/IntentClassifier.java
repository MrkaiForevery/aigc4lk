package com.air.commander.intent;

import com.air.commander.Prompt.PromptManagerBuilder;
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
import java.util.Objects;

@Slf4j
@Component
public class IntentClassifier {

    private static final String FAST_MODEL_CLIENT = "fastModelClient";
    private static final String LLM_FAST_MODEL = "llm-fast-model";
    private static final String FILED_PREDEFINED = "predefined";
    private static final String FILED_SCENARIO = "scenario";
    private static final String FILED_COMPLEXITY = "complexity";

    private final RemoteConfigLoader configLoader;
    private final ChatClient chatClient;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;
    private final PromptManagerBuilder promptManagerBuilder;

    public IntentClassifier(RemoteConfigLoader configLoader,
                            @Qualifier(FAST_MODEL_CLIENT) ChatClient fastchatClient,
                            ResilienceManager resilienceManage,
                            PromptManagerBuilder promptManagerBuilder,
                            ObjectMapper objectMapper) {
        this.configLoader = configLoader;
        this.chatClient = fastchatClient;
        this.resilienceManager = resilienceManage;
        this.promptManagerBuilder = promptManagerBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 对用户输入进行意图分类
     * 逻辑流程如下：
     * 第一层: 硬规则快速预定义模板匹配 ——>直接看userInput = rule.keyWords[index]
     * 第二层: 使用轻量级LLM进行预定义模板模糊匹配 ——>让LLM自主分析去匹配预先定义的模板
     * 第三层: 如果未匹配到相应的模板或异常情形 ——>则直接返回没有带场景标识的IntentResult -->命令后续的编排器进行自主意图编排任务流程
     *
     * @param userInput     原始用户输入
     * @param memoryContext 当前会话的记忆上下文（已通过 threadId 隔离）
     * @return 意图结果，包含是否匹配模板、场景标识、模板ID、复杂度
     */
    public IntentResult classify(String userInput, MemoryContext memoryContext) {
        // ========== 硬规则快速预定义模板匹配 ==========
        log.debug("硬规则匹配开始......");
        IntentResult hardResult = this.templateMatchesHard(userInput);
        if (hardResult != null) return hardResult;

        // ========== 使用轻量级LLM进行预定义模板模糊匹配 ==========
        log.debug("硬规则未匹配，启动 LLM 意图分析匹配");
        return templateVagueMatchesByFastLLM(userInput, memoryContext);
    }

    private IntentResult templateVagueMatchesByFastLLM(String userInput, MemoryContext memoryContext) {
        String prompt = promptManagerBuilder.buildIntentClassifierVagueMatchesPrompt(userInput, memoryContext, this.configLoader);
        String llmOutput = callLLMWithFallback(prompt);
        return parseIntentResponse(llmOutput);
    }

    /**
     * 调用轻量级 LLM，失败时降级返回默认 JSON
     */
    private String callLLMWithFallback(String prompt) {
        return resilienceManager.executeWithFullProtection(
                LLM_FAST_MODEL,
                () -> chatClient.prompt(prompt).call().content(),
                this::fallbackLlmOutput
        );
    }

    /**
     * 解析 LLM 输出的 JSON 并转换为 IntentResult
     */
    private IntentResult parseIntentResponse(String llmOutput) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(llmOutput, Map.class);
            return extractIntent(responseMap);
        } catch (JsonProcessingException e) {
            log.error("解析 LLM 意图分类结果失败, 降级为动态编排", e);
            return IntentResult.dynamic(3);
        }
    }

    /**
     * 从已解析的 Map 中提取意图信息
     */
    private IntentResult extractIntent(Map<String, Object> responseMap) {
        boolean predefined = Boolean.TRUE.equals(responseMap.get(FILED_PREDEFINED));
        int complexity = getComplexity(responseMap);

        if (predefined && responseMap.containsKey(FILED_SCENARIO)) {
            String scenario = (String) responseMap.get(FILED_SCENARIO);
            String templateId = configLoader.getScenarioToTemplateId().get(scenario);
            if (templateId != null) {
                log.info("LLM 匹配到预定义场景: scenario={}, templateId={}", scenario, templateId);
                return IntentResult.template(scenario, templateId, complexity);
            }
        }

        log.info("LLM 判断为非预定义场景，使用动态编排, complexity={}", complexity);
        return IntentResult.dynamic(complexity);
    }

    /**
     * 安全地从 Map 中提取复杂度，默认返回 3
     */
    private int getComplexity(Map<String, Object> responseMap) {
        Object value = responseMap.get(FILED_COMPLEXITY);
        return value instanceof Number ? ((Number) value).intValue() : 3;
    }


    private IntentResult templateMatchesHard(String userInput) {
        IntentResult result = configLoader.getIntentRules().stream()
                .filter(rule -> rule.matches(userInput))
                .map(rule -> {
                    String templateId = configLoader.getScenarioToTemplateId().get(rule.scenario());
                    if (templateId != null) {
                        log.debug("硬规则匹配成功: scenario={}, templateId={}", rule.scenario(), templateId);
                        return new IntentResult(true, rule.scenario(), templateId, rule.complexity());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (result != null) {
            return result;
        }
        return null;
    }


    /**
     * LLM 调用失败时的兜底输出
     */
    private String fallbackLlmOutput() {
        return "{\"predefined\": false, \"complexity\": 3}";
    }
}