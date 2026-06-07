package com.air.commander.Prompt;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.Step;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.AgentSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提示词构建类
 * 核心能力: 统一管理commanderAgent内所有LLM发起的提示词构建
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptManagerBuilder {

    private final ObjectMapper objectMapper;

    /**
     * IntentClassifier意图分析的时使用LLM匹配模板动作时需要的Prompt提示词
     */
    public String buildIntentClassifierVagueMatchesPrompt(String userInput, MemoryContext ctx, RemoteConfigLoader configLoader) {
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

        // 仅取最近20条,在前面build这个ctx的时候就已经进行了条数处理
        if (ctx.getRecentMessages() != null && !ctx.getRecentMessages().isEmpty()) {
            String recentMessages = ctx.getRecentMessages().stream()
                    .map(msg -> "- " + msg.getRole() + ": " + msg.getContent())
                    .collect(Collectors.joining("\n"));
            sb.append("=== 最近对话 ===\n").append(recentMessages).append("\n\n");
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
     * GraphExecutor执行图流程时，当step步骤是LLM类型时需要的Prompt提示词
     */
    public String buildGraphExecutorLLMStepPrompt(Step step, Map<String, Object> context, MemoryContext memoryCtx) {
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
                if (!"userQuery" .equals(key)) {
                    sb.append(key).append(": ").append(formatValue(value)).append("\n");
                }
            });
            sb.append("\n");
        }

        // ========= 3. 对话历史（只取最近 3 条，过滤占位符） =========
        if (memoryCtx.getRecentMessages() != null && !memoryCtx.getRecentMessages().isEmpty()) {
            String recent = memoryCtx.getRecentMessages().stream()
                    .filter(msg -> !"done" .equals(msg.getContent()))   // 过滤无效消息
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
     * 在DynamicOrchestrator中LLM执行GeneratePlan时所需要的提示词
     */
    public String buildDynamicOrchestratorGeneratePlanPrompt(String userInput, MemoryContext ctx, Set<AgentCardWrapper> agents) {
        StringBuilder sb = new StringBuilder();

        // ========= 角色与任务 =========
        sb.append("你是一个智能任务规划器。你的唯一任务是将用户请求转化为一个结构化的 JSON 执行计划。\n");
        sb.append("你必须仔细分析用户意图，结合可用 Agent 的能力，分解出具体的执行步骤，并选择最合适的执行模式。\n\n");

        // ========= 分析步骤（引导模型思考） =========
        sb.append("【分析流程】请按以下步骤思考（但最终只输出 JSON）：\n");
        sb.append("1. 用户核心需求是什么？可以用一个自然语言句子概括。\n");
        sb.append("2. 要实现这个需求，需要哪些子任务？\n");
        sb.append("3. 每个子任务能否由某个可用的 Agent 完成？如果没有合适的 Agent，则使用 LLM_CALL。\n");
        sb.append("4. 这些子任务之间有什么依赖关系？可以并行吗？需要条件分支吗？需要反复修正吗？\n");
        sb.append("5. 根据任务结构，选择最合适的全局执行模式（见下方定义）。\n\n");

        // ========= 执行模式详细定义 =========
        sb.append("【执行模式定义】\n");
        sb.append("- SEQUENTIAL: 步骤必须按顺序依次执行。如果任务有明确的先后依赖（例如“先分析数据再生成报告”），选择此模式。\n");
        sb.append("- PARALLEL: 多个子任务互不依赖，可以同时执行以节省时间。\n");
        sb.append("- CONDITIONAL: 执行路径取决于中间结果，例如“如果分析结果异常，则执行A步骤，否则执行B步骤”。\n");
        sb.append("- ITERATIVE_CORRECTION: 需要反复执行“执行→评估→修正”循环，直到满足某个质量标准（如生成一份完美的文档）。\n");
        sb.append("- COMPETITIVE: 让多个 Agent 同时执行同一个任务，然后选择最优结果（适合高难度或需要备选方案的任务）。\n");
        sb.append("- PIPELINE: 前一个步骤的输出直接作为后一个步骤的输入，形成流式处理线。\n\n");

        // ========= 任务拆分原则 =========
        sb.append("【任务拆分规则】\n");
        sb.append("1. 每个步骤必须是一个原子化的、可独立执行的指令。\n");
        sb.append("2. 步骤的 task 字段必须包含具体的动作和对象，例如：\n");
        sb.append("   - 错误: \"分析数据\"\n");
        sb.append("   - 正确: \"对用户的销售数据进行多维分析，输出趋势图和关键指标\"\n");
        sb.append("3. 如果某个步骤需要了解用户的原始意图，你必须将其完整放入 input.userQuery 字段。\n");
        sb.append("4. A2A_DELEGATE 步骤的 agent 字段必须是可用 Agent 列表中的名称，不能虚构。\n");
        sb.append("5. 尽量将步骤数量控制在 3 个以内，除非任务确实很复杂。\n\n");

        // 1. 可用 Agent 列表（包含能力描述）
        sb.append("=== 可用Agent ===\n");
        for (AgentCardWrapper agent : agents) {
            sb.append("- ").append(agent.name());
            if (agent.description() != null && !agent.description().isBlank()) {
                sb.append(": ").append(agent.description());
            }
            if (agent.skills() != null && !agent.skills().isEmpty()) {
                String skillNames = agent.skills().stream()
                        .map(AgentSkill::name)
                        .collect(Collectors.joining(", "));
                sb.append("，技能:").append(skillNames);
            }
            sb.append("\n");
        }
        sb.append("\n");

        // 2. 用户画像todo 先不拼这个，容易污染大模型意图
//        if (ctx.getUserProfile() != null && !ctx.getUserProfile().isEmpty()) {
//            sb.append("=== 用户画像 ===\n");
//            sb.append(ctx.getUserProfile()).append("\n\n");
//        }
//
//        // 3. 用户偏好todo 先不拼这个，容易污染大模型意图
//        if (ctx.getPreferences() != null && !ctx.getPreferences().isEmpty()) {
//            sb.append("=== 用户偏好 ===\n");
//            sb.append(ctx.getPreferences()).append("\n\n");
//        }

        // 4. 最近对话（取最近3条，帮助理解上下文连续性）todo 先不拼这个，容易污染大模型意图
//        if (ctx.getRecentMessages() != null && !ctx.getRecentMessages().isEmpty()) {
//            String recentMessages = ctx.getRecentMessages().stream()
//                    .filter(msg -> !"done" .equals(msg.getContent()))
//                    .map(msg -> "- " + msg.getRole() + ": " + msg.getContent())
//                    .collect(Collectors.joining("\n"));
//            sb.append("=== 最近对话 ===\n").append(recentMessages).append("\n\n");
//        }

        // ===== 当前用户请求 =====
        sb.append("=== 当前用户请求 ===\n").append(userInput).append("\n\n");

        // 5. 相似历史案例（Few-shot）todo 先不拼这个，容易污染大模型意图
//        if (ctx.getSimilarCases() != null && !ctx.getSimilarCases().isEmpty()) {
//            sb.append("=== 参考成功案例 ===\n");
//            int count = Math.min(3, ctx.getSimilarCases().size());
//            for (int i = 0; i < count; i++) {
//                sb.append("案例").append(i + 1).append(": ")
//                        .append(ctx.getSimilarCases().get(i)).append("\n");
//            }
//            sb.append("\n");
//        }

        // ========= 输出格式要求（强制 JSON） =========
        sb.append("你必须输出一个完整的 JSON 对象，包含以下字段：\n");
        sb.append("- executionMode: 字符串，取值必须是 SEQUENTIAL/PARALLEL/CONDITIONAL/ITERATIVE_CORRECTION/COMPETITIVE/PIPELINE 之一。\n");
        sb.append("- steps: 数组，每个元素是一个对象，包含：\n");
        sb.append("    - id: 步骤唯一标识（如 step1）。\n");
        sb.append("    - type: 步骤类型，A2A_DELEGATE/LLM_CALL/INTERRUPT 之一。\n");
        sb.append("    - agent: 当 type 为 A2A_DELEGATE 时必须，取值为可用 Agent 名称。\n");
        sb.append("    - task: 具体任务描述，必须是一个完整自然语言句子。\n");
        sb.append("    - input: 对象，必须包含 'userQuery'（值为当前用户请求全文），以及步骤所需的其它参数。\n");
        sb.append("    - dependsOn: 字符串数组，列出本步骤依赖的前置步骤 id，无依赖则为空数组。\n\n");

        // ========= 正确示例 =========
        sb.append("【正确示例】\n");
        sb.append("用户请求：\"帮我分析最近的销售数据并生成报告\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"A2A_DELEGATE\",\n");
        sb.append("      \"agent\": \"data-analysis-agent\",\n");
        sb.append("      \"task\": \"提取并分析最近一个季度的销售数据，输出趋势图和关键指标\",\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析最近的销售数据并生成报告\", \"dataRange\": \"last_quarter\"},\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step2\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"根据分析结果撰写一份专业的销售报告，包含摘要、图表描述和趋势预测\",\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析最近的销售数据并生成报告\", \"analysisResult\": \"{step1.output}\"},\n");
        sb.append("      \"dependsOn\": [\"step1\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        // ========= 最终指令 =========
        sb.append("现在，根据以上所有信息，为当前用户请求生成一个 JSON 执行计划。");
        sb.append("直接输出 JSON，不要包含任何额外文字、注释或 Markdown 标记。");

        return sb.toString();
    }
}