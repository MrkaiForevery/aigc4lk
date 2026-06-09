package com.air.commander.Prompt;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.Step;
import com.air.commander.orchestrator.CandidateGenerator;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.AgentSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
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
        sb.append("你是一个意图分类助手。根据用户请求判断属于哪个预定义场景，并评估复杂度和风险。\n\n");

        // 预定义场景列表
        sb.append("=== 预定义场景 ===\n");
        configLoader.getScenarioToTemplateId().keySet().forEach(scenario ->
                sb.append("- ").append(scenario).append("\n")
        );
        sb.append("\n");

        // 用户画像
        if (ctx.getUserProfile() != null && !ctx.getUserProfile().isEmpty()) {
            sb.append("=== 用户画像 ===\n").append(ctx.getUserProfile()).append("\n\n");
        }

        // 用户偏好
        if (ctx.getPreferences() != null && !ctx.getPreferences().isEmpty()) {
            sb.append("=== 用户偏好 ===\n").append(ctx.getPreferences()).append("\n\n");
        }

        // 最近对话（取最近3条）
        if (ctx.getRecentMessages() != null && !ctx.getRecentMessages().isEmpty()) {
            String recent = ctx.getRecentMessages().stream()
                    .filter(msg -> !"done".equals(msg.getContent()))
                    .skip(Math.max(0, ctx.getRecentMessages().size() - 3))
                    .map(msg -> "- " + msg.getRole() + ": " + msg.getContent())
                    .collect(Collectors.joining("\n"));
            if (!recent.isEmpty()) {
                sb.append("=== 最近对话 ===\n").append(recent).append("\n\n");
            }
        }

        // 当前请求
        sb.append("=== 当前用户请求 ===\n").append(userInput).append("\n\n");

        // 复杂度评估标准
        sb.append("【复杂度评估标准】\n");
        sb.append("- 1-2：简单查询或单步任务\n");
        sb.append("- 3：需要2-3个步骤或简单分析\n");
        sb.append("- 4：需要多步分析、调用外部服务或涉及敏感数据\n");
        sb.append("- 5：高度复杂，需要多个Agent协作或人工干预\n\n");

        // 风险提示（辅助判断）
        sb.append("【高风险信号】（如果出现以下情况，complexity至少为4，且可能需要检查点）\n");
        sb.append("- 涉及财务、医疗、隐私等敏感数据\n");
        sb.append("- 需要发送邮件、扣款、发布内容等不可逆操作\n");
        sb.append("- 用户明确要求“确认”或“审核”\n\n");

        // 输出格式
        sb.append("请以 JSON 格式返回（不要包含其他内容）：\n");
        sb.append("{ \"predefined\": true/false, \"scenario\": \"场景标识（仅predefined=true时必填）\", \"complexity\": 1-5, \"highRisk\": true/false }");

        return sb.toString();
    }
    /**
     * GraphExecutor执行图流程时，当step步骤是LLM类型时需要的Prompt提示词
     */
    public String buildGraphExecutorLLMStepPrompt(Step step, Map<String, Object> context, MemoryContext memoryCtx) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        log.info("LLM步骤 {} 的 context keys: {}", step.getId(), context.keySet());

        // ========= 1. 任务描述（解析占位符） =========
        String resolvedTask = replacePlaceholders(step.getTask(), context);
        sb.append("【任务】\n").append(resolvedTask).append("\n\n");

        // ========= 2. 输入数据（已解析占位符） =========
        if (step.getInput() != null && !step.getInput().isEmpty()) {
            Map<String, Object> resolvedInput = resolveInput(step.getInput(), context);
            //在顺序模式下执行LLM_CALL时，剔除用户的原始请求输入，避免大模型产生幻觉
            boolean hasBusinessData = resolvedInput.entrySet().stream()
                    .anyMatch(entry -> !"userQuery".equals(entry.getKey()) && entry.getValue() != null);
            if (hasBusinessData) {
                sb.append("【输入数据】\n");
                resolvedInput.forEach((key, value) -> {
                    // 不再输出 userQuery
                    if (!"userQuery".equals(key) && value != null) {
                        sb.append(key).append(": ").append(formatValue(value)).append("\n");
                    }
                });
                sb.append("\n");
            }
        }

        // ========= 3. 对话历史（只取最近 3 条，过滤占位符)=========
        if (step.isIncludeChatHistory() && memoryCtx.getRecentMessages() != null) {
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
    private Map<String, Object> resolveInput(Map<String, Object> input, Map<String, Object> context) throws JsonProcessingException {
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
    private Object resolveValue(Object value, Map<String, Object> context) throws JsonProcessingException {
        if (value instanceof String str) {
            // 完全匹配 {xxx} ：直接返回上下文中的原始对象
            if (str.matches("\\{[^}]+\\}")) {
                String refKey = str.substring(1, str.length() - 1);
                Object ctxValue = context.get(refKey);
                if (ctxValue != null) {
                    return truncateContextValue(ctxValue);  // ← 截断
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
                    .map(item -> {
                        try {
                            return resolveValue(item, context);
                        } catch (JsonProcessingException e) {
                            log.error("解析失败");
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }
        // 其他类型（数字、布尔等）直接返回
        return value;
    }

    /**
     * 替换字符串中所有 {key} 占位符为上下文中的字符串值
     */
    public String replacePlaceholders(String template, Map<String, Object> context) {
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
     * 对从 context 中取出的值进行长度控制
     */
    private Object truncateContextValue(Object value) throws JsonProcessingException {
        if (value instanceof String str) {
            if (str.length() > 4000) {
                return str.substring(0, 4000) + "\n...(内容过长，已截断，完整数据可引用占位符获取)";
            }
            return str;
        }
        if (value instanceof Map map) {
            String json = objectMapper.writeValueAsString(map);
            if (json.length() > 4000) {
                // 保留 map 中第一个 key 的完整内容，其余截断
                // 或者转为摘要字符串
                return json.substring(0, 4000) + "...(已截断)";
            }
            return value; // 如果 Map 不大，保持原样
        }
        return value;
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
        sb.append("5. 分析每个子任务的风险等级，判断是否需要人工干预（见下方检查点规则）。\n");
        sb.append("6. 根据任务结构，选择最合适的全局执行模式（见下方定义）。\n\n");

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
        sb.append("5. 尽量将步骤数量控制少于或等于5个以内，除非任务确实很复杂可以在大于5个，但最多不能超过10个。\n\n");
        sb.append("6. 步骤的 input 中如需引用前序步骤的输出，必须使用 {stepX.output} 格式的占位符，不要把前序步骤的完整输出直接写在 input 中。\n");
        sb.append("   示例：\"carData\": \"{step1.output}\" 而不是 \"carData\": \"（这里写几万字的完整分析报告）\"\n");
        sb.append("7. 如果某个步骤的 input 中已经包含了前序步骤的完整输出数据，那么该步骤的 task 应该描述如何处理这些数据，而不是重新获取或搜索相同的内容。\n");
        sb.append("   例如：如果 step2 已经输出了10款车型的口碑评价，step3 的 task 应为 \"根据已有的口碑评价精选2款最佳车型\"，而不是 \"搜索口碑评价\"。\n");
        sb.append("8. 必须确保每个步骤的 task 与其 input 中的数据相匹配，不要描述与 input 内容重复或冲突的动作。\n");
        sb.append("9. 如果某个 LLM_CALL 步骤需要结合用户之前的对话历史才能准确执行（例如“根据刚才讨论的内容进行总结”或“参考之前的对话回答”），\n");
        sb.append("   请在 input 中设置 \"includeChatHistory\": true，否则不要包含此字段或设置为 false。\n\n");

        // ========= 检查点规则（新增，融合到原有逻辑中） =========
        sb.append("【检查点（人工干预）规则】\n");
        sb.append("你必须分析每个子任务的风险等级，判断是否需要插入 type=INTERRUPT 的步骤作为检查点，在关键操作前让用户确认或授权。\n\n");
        sb.append("以下情况必须插入 CREDENTIAL 类型检查点（需要用户授权权限）：\n");
        sb.append("  - 访问敏感数据或受保护资源（如财务数据、医疗记录、用户隐私信息）\n");
        sb.append("  - 调用需要特殊权限的 API 或系统（如财务系统、CRM系统）\n");
        sb.append("  - 示例：用户要求\"分析财务数据\"，在调用财务系统前插入授权检查点\n\n");
        sb.append("以下情况必须插入 CONFIRM 类型检查点（需要用户确认操作）：\n");
        sb.append("  - 执行不可逆操作（如发送邮件、扣款、发布内容、删除数据）\n");
        sb.append("  - 关键决策点，需要用户确认中间结果（如生成正式报告、执行重要分析）\n");
        sb.append("  - 输出结果可能产生重大影响（如发送给老板的报告、对外发布的内容）\n");
        sb.append("  - 示例：数据分析完成后，插入确认点让用户验证数据后再生成报告\n\n");
        sb.append("以下情况不需要检查点：\n");
        sb.append("  - 纯信息查询（如\"介绍一下最新的AI技术\"）\n");
        sb.append("  - 简单的文本生成或格式转换\n");
        sb.append("  - 用户明确表示\"直接执行\"或\"不需要确认\"\n\n");
        sb.append("如果不需要人工干预，则不要插入 INTERRUPT 步骤。\n\n");

        sb.append("=== 可用 Agent 能力清单 ===\n");
        sb.append("以下是当前所有可用的 Agent，请严格根据它们的技能分配任务。\n\n");
        int index = 1;
        for (AgentCardWrapper agent : agents) {
            sb.append("Agent ").append(index).append("：").append(agent.name()).append("\n");
            if (agent.description() != null && !agent.description().isBlank()) {
                sb.append("  - 简介：").append(agent.description()).append("\n");
            }
            if (agent.skills() != null && !agent.skills().isEmpty()) {
                sb.append("  - 技能列表：\n");
                agent.skills().forEach(skill -> sb.append("    · ").append(skill.name()).append("\n"));
            } else {
                sb.append("  - 技能列表：（无明确技能描述，可承担通用文本处理任务）\n");
            }
            sb.append("\n");
            index++;
        }
        sb.append("注意：当 type 为 A2A_DELEGATE 时，agent 字段必须使用上述 Agent 名称之一。\n\n");

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
        sb.append("    - dependsOn: 字符串数组，列出本步骤依赖的前置步骤 id，无依赖则为空数组。\n");
        sb.append("    - checkpoint: 当 type 为 INTERRUPT 时可选，包含以下子字段：\n");
        sb.append("        - type: \"CREDENTIAL\" 或 \"CONFIRM\"\n");
        sb.append("        - question: 向用户展示的问题\n");
        sb.append("        - requiredScopes: 字符串数组（CREDENTIAL 类型必填）\n");
        sb.append("        - timeoutMinutes: 超时分钟数，默认 30\n\n");
        sb.append("    - includeChatHistory: 布尔值，仅当 type 为 LLM_CALL 且需要对话历史时才设置为 true，否则可省略或设为 false。\n\n");

        // ========= 正确示例（增强版，包含检查点） =========
        sb.append("【正确示例1：无检查点的简单任务】\n");
        sb.append("用户请求：\"介绍一下最新的AI技术\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"检索并介绍当前最新的AI技术趋势\",\n");
        sb.append("      \"input\": {\"userQuery\": \"介绍一下最新的AI技术\"},\n");
        sb.append("      \"includeChatHistory\": false,\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("【正确示例1：无检查点的简单任务】\n");
        sb.append("用户请求：\"介绍一下最新的AI技术\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"检索并介绍当前最新的AI技术趋势\",\n");
        sb.append("      \"input\": {\"userQuery\": \"介绍一下最新的AI技术\"},\n");
        sb.append("      \"includeChatHistory\": false,\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("【正确示例2：需要对话历史的 LLM 步骤】\n");
        sb.append("用户请求：\"根据我们刚才讨论的内容，总结一下推荐的车型\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"根据对话历史中讨论的车型推荐，总结出最终的推荐列表\",\n");
        sb.append("      \"input\": {\"userQuery\": \"根据我们刚才讨论的内容，总结一下推荐的车型\"},\n");
        sb.append("      \"includeChatHistory\": true,\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("【正确示例3：包含检查点的敏感任务】\n");
        sb.append("用户请求：\"帮我分析财务数据并发送报告给老板\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"INTERRUPT\",\n");
        sb.append("      \"task\": \"请求用户授权访问财务数据\",\n");
        sb.append("      \"checkpoint\": {\n");
        sb.append("        \"type\": \"CREDENTIAL\",\n");
        sb.append("        \"question\": \"即将访问财务敏感数据，需要您的授权\",\n");
        sb.append("        \"requiredScopes\": [\"financial_read\"],\n");
        sb.append("        \"timeoutMinutes\": 30\n");
        sb.append("      },\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析财务数据并发送报告给老板\"},\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step2\",\n");
        sb.append("      \"type\": \"A2A_DELEGATE\",\n");
        sb.append("      \"agent\": \"data-analysis-agent\",\n");
        sb.append("      \"task\": \"提取并分析最近一个季度的财务数据，输出趋势图和关键指标\",\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析财务数据并发送报告给老板\", \"dataRange\": \"last_quarter\"},\n");
        sb.append("      \"dependsOn\": [\"step1\"]\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step3\",\n");
        sb.append("      \"type\": \"INTERRUPT\",\n");
        sb.append("      \"task\": \"确认分析结果后是否发送报告\",\n");
        sb.append("      \"checkpoint\": {\n");
        sb.append("        \"type\": \"CONFIRM\",\n");
        sb.append("        \"question\": \"财务分析已完成，请确认是否发送报告给老板？\",\n");
        sb.append("        \"timeoutMinutes\": 30\n");
        sb.append("      },\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析财务数据并发送报告给老板\", \"analysisResult\": \"{step2.output}\"},\n");
        sb.append("      \"dependsOn\": [\"step2\"]\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step4\",\n");
        sb.append("      \"type\": \"A2A_DELEGATE\",\n");
        sb.append("      \"agent\": \"email-agent\",\n");
        sb.append("      \"task\": \"将财务分析报告发送给老板\",\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析财务数据并发送报告给老板\", \"report\": \"{step2.output}\", \"recipient\": \"boss@company.com\"},\n");
        sb.append("      \"dependsOn\": [\"step3\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        // ========= 最终指令 =========
        sb.append("现在，根据以上所有信息，为当前用户请求生成一个 JSON 执行计划。");
        sb.append("直接输出 JSON，不要包含任何额外文字、注释或 Markdown 标记。");

        return sb.toString();
    }


    /**
     * 在PlanEvaluator中LLM执行evaluate时所需要的提示词
     */
    public String buildEvaluationPrompt(List<CandidateGenerator.CandidatePlan> candidates,
                                         String userInput, MemoryContext memoryCtx) {
        StringBuilder sb = new StringBuilder();

        // ========= 角色与任务 =========
        sb.append("你是一个执行计划评审专家。请根据以下标准，从多个候选执行计划中选出最优的一个。\n");
        sb.append("如果某个候选在关键维度上存在严重缺陷（如 Agent 误用），应直接给予低分，切勿选出。\n\n");

        // ========= 用户需求 =========
        sb.append("=== 用户需求 ===\n");
        sb.append(userInput).append("\n\n");

        // 选择性附上用户画像和偏好（避免噪声）
//        if (memoryCtx != null) {
//            if (memoryCtx.getUserProfile() != null && !memoryCtx.getUserProfile().isEmpty()) {
//                sb.append("用户画像：").append(memoryCtx.getUserProfile()).append("\n");
//            }
//            if (memoryCtx.getPreferences() != null && !memoryCtx.getPreferences().isEmpty()) {
//                sb.append("用户偏好：").append(memoryCtx.getPreferences()).append("\n");
//            }
//        }
//        sb.append("\n");

        // ========= 评估标准（强化常见错误警示） =========
        sb.append("【评估标准】\n");
        sb.append("1. Agent 准确性 (agentAccuracy)：Agent 的使用是否与可用 Agent 的能力严格匹配？\n");
        sb.append("   - ⚠️ document-agent 只能用于文档生成、报告撰写，绝对不可用于网络搜索、数据收集。\n");
        sb.append("   - 若某候选误将搜索任务分配给 document-agent，该维度应评为 1-3 分，且该候选不应成为 winner。\n");
        sb.append("2. 数据流完整性 (dataFlow)：每个步骤的 input 是否包含了完成任务所需的数据？\n");
        sb.append("   - 依赖前序步骤的步骤，input 中必须使用 {stepX.output} 引用前序输出。\n");
        sb.append("3. 检查点合理性 (checkpoint)：高风险步骤或不可逆操作前是否合理插入了 INTERRUPT 检查点？\n");
        sb.append("4. 步骤效率 (efficiency)：步骤数量是否适中（3~5 步为佳）？是否存在可以合并的冗余步骤？\n\n");

        // ========= 候选计划展示（精简序列化） =========
        sb.append("=== 候选计划 ===\n");
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            CandidateGenerator.CandidatePlan c = candidates.get(i);
            char label = (char) ('A' + i);
            labels.add(String.valueOf(label));

            sb.append("候选 ").append(label).append("（模型：").append(c.choseChatClientName()).append("）：\n");
            try {
                // 只序列化关键字段，避免输出大量 null
                Map<String, Object> planMap = objectMapper.convertValue(c.plan(), Map.class);
                // 去除所有 null 值的字段
                removeNullValues(planMap);
                sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(planMap));
            } catch (Exception e) {
                sb.append("（计划序列化失败）");
            }
            sb.append("\n\n");
        }

        // ========= 输出要求 =========
        sb.append("请对上述 ").append(candidates.size()).append(" 个候选计划分别评分，并选出最优。\n");
        sb.append("评分维度：agentAccuracy、dataFlow、checkpoint、efficiency，每项 1-10 分。\n");
        sb.append("注意：若某候选 agentAccuracy 得分低于 5，则不应选为 winner。\n\n");

        // 动态生成评分格式示例，避免尾随逗号
        sb.append("输出 JSON 格式（不要包含其他内容）：\n");
        sb.append("{\n");
        sb.append("  \"winner\": \"胜出的候选标签\",\n");
        sb.append("  \"scores\": {\n");
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            sb.append("    \"").append(label).append("\": {\"agentAccuracy\": 8, \"dataFlow\": 7, \"checkpoint\": 9, \"efficiency\": 8}");
            // 最后一个对象不加逗号
            if (i < labels.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  },\n");
        sb.append("  \"reason\": \"选择该候选的简要理由\"\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * 递归移除 Map 中所有值为 null 的键值对，减少序列化噪音
     */
    private void removeNullValues(Map<String, Object> map) {
        map.entrySet().removeIf(entry -> entry.getValue() == null);
        for (Object value : map.values()) {
            if (value instanceof Map) {
                removeNullValues((Map<String, Object>) value);
            }
        }
    }
}