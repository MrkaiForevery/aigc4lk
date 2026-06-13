package com.air.commander.Prompt;

import com.air.commander.chat.ChatClientSelector;
import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.Step;
import com.air.commander.orchestrator.CandidateGenerator;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ChatClientSelector chatClientSelector;

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

//        // 风险提示（辅助判断）
//        sb.append("【高风险信号】（如果出现以下情况，complexity至少为4，且可能需要检查点）\n");
//        sb.append("- 涉及财务、医疗、隐私等敏感数据\n");
//        sb.append("- 需要发送邮件、扣款、发布内容等不可逆操作\n");
//        sb.append("- 用户明确要求“确认”或“审核”\n\n");

        // 输出格式
        sb.append("请以 JSON 格式返回（不要包含其他内容）：\n");
        sb.append("{ \"predefined\": true/false, \"scenario\": \"场景标识（仅predefined=true时必填）\", \"complexity\": 1-5, \"highRisk\": true/false }");

        return sb.toString();
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
        sb.append("- PARALLEL: 多个子任务互不依赖，可以同时执行以节省时间。如果任务包含多个独立的子任务（例如同时搜集产品信息、用户评价、价格数据），应优先使用此模式。\n");
        sb.append("- CONDITIONAL: 执行路径取决于中间结果。当任务需要根据某个分析结果动态选择后续步骤时使用。\n");
        sb.append("  例如：“如果销售下滑则生成应对方案，否则生成总结报告”。此时需要插入一个条件判断步骤（LLM_CALL），其输出决定后续跳转。\n");
        sb.append("- ITERATIVE_CORRECTION: 需要反复执行“执行→评估→修正”循环，直到满足某个质量标准。适合生成高质量内容或需要自我完善的场景。\n");
        sb.append("  可以定义多个独立的循环闭环，每个循环包含一组主步骤、一个评估步骤和一个可选的修正步骤。\n");
        sb.append("- COMPETITIVE: 让多个 Agent 同时执行同一个任务，然后选择最优结果。\n");

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
        sb.append("10. 当使用 COMPETITIVE 模式时，同一竞争组内的不同竞争者必须使用不同的模型（model 字段），以保证方案多样性。\n");
        sb.append("   每个竞争者可以是一个或多个步骤的串联（通过 stepIds 指定），评审步骤的 input 中应引用该组的聚合输出 {groupId.output}。\n\n");
        sb.append("11. 建议所有 type 为 LLM_CALL 的步骤都显式指定 model 字段，从【可用大模型列表】中选择，以避免因默认模型变更导致执行结果不一致。\n");


        // ========= 条件分支 (CONDITIONAL) 规则 =========
        sb.append("【条件分支 (CONDITIONAL) 规则】\n");
        sb.append("当 executionMode 为 CONDITIONAL 时，必须遵循以下规则：\n");
        sb.append("1. 条件判断步骤必须是 LLM_CALL 类型。\n");
        sb.append("2. 条件步骤的 task 必须明确要求输出一个简短的分类标签（如“只输出一个词：下滑/平稳/增长”），该标签将作为选择分支的依据。\n");
        sb.append("3. 条件步骤需包含 conditionConfig 对象，用于声明条件逻辑：\n");
        sb.append("   - expression: 条件描述，例如“判断销售趋势”或 SPEL 表达式。\n");
        sb.append("   - evaluationMethod: 评估方式，\"LLM_JUDGE\"（由大模型输出分类标签）或 \"SPEL\"（表达式求值）。当前系统主要使用 \"LLM_JUDGE\"。\n");
        sb.append("   - branches: 对象，key 为可能的分类标签（如“下滑”），value 为对应要跳转的目标步骤 ID。\n");
        sb.append("   - defaultStepId: 默认分支的步骤 ID，当大模型输出无法匹配 branches 中的任何标签时使用。\n");
        sb.append("4. 同时，条件步骤的 input 字段中必须包含 branches 和 default，其内容与 conditionConfig 中的 branches、defaultStepId 保持一致。执行引擎实际路由时依赖 input 中的这些字段。\n");
        sb.append("5. 所有分支步骤（例如 step_fix, step_summary 等）的 dependsOn 必须包含条件步骤的 ID。\n");
        sb.append("6. 步骤列表中只应包含条件步骤之后可能被执行到的分支步骤，不应出现永远不会被引用的步骤。\n\n");

        // ========= 循环纠正 (ITERATIVE_CORRECTION) 规则（完整版） =========
        sb.append("【循环纠正 (ITERATIVE_CORRECTION) 规则】\n");
        sb.append("当 executionMode 为 ITERATIVE_CORRECTION 时，必须遵循以下规则：\n");
        // 1. 评估步骤
        sb.append("1. 计划中必须包含至少一个评估步骤（LLM_CALL），其 task 要求输出一个 JSON 对象，格式为：{\"score\": 85, \"issues\": [\"问题1\", \"问题2\"]}。\n");
        sb.append("   其中 score 是 0-100 的整数评分，issues 是发现的具体问题列表（可选）。\n");
        // 2. 主步骤序列
        sb.append("2. 评估步骤之前的步骤是主步骤序列（即循环体），它们会被循环执行和修正。\n");
        sb.append("   主步骤可以是一个或多个，它们之间的依赖关系通过 dependsOn 声明。\n");
        // 3. 修正步骤
        sb.append("3. 评估步骤之后可以有一个修正步骤（LLM_CALL 或 A2A_DELEGATE），用于根据评估反馈改进主步骤的输出。\n");
        sb.append("   修正步骤的 input 中必须包含评估步骤的输出（通过 {step_evaluate.output} 引用）和需要修正的原始内容。\n");
        sb.append("   如果不存在修正步骤，未达标时将直接重新执行主步骤。\n");
        // 4. 配置
        sb.append("4. 在 correctionConfig 中指定以下字段：\n");
        sb.append("   - evaluatorStepId: 评估步骤的 ID（必填）。\n");
        sb.append("   - correctorStepId: 修正步骤的 ID（可选，无修正步骤则省略）。\n");
        sb.append("   - maxIterations: 最大迭代次数（必填，建议 3-5 次）。\n");
        sb.append("   - qualityThreshold: 质量阈值 0-100（必填，建议 80-90）。\n");
        sb.append("   - checkpointAfterEachIteration: 是否在每轮循环后插入确认检查点（可选，默认 false）。\n");
        sb.append("   - checkpointOnMaxIterations: 达到最大迭代次数后是否插入检查点（可选，默认 true）。\n");
        // 5. 检查点插入位置
        sb.append("5. 检查点可以插入在以下位置：\n");
        sb.append("   - 修正步骤之前：让用户确认是否需要修正（避免自动修正越改越差）。\n");
        sb.append("   - 评估步骤之后、修正步骤之前：展示评估结果，让用户决定是否继续。\n");
        sb.append("   - 主步骤序列中的高风险操作之前：如发送邮件、扣款等。\n");
        // 6. 数据流
        sb.append("6. 循环内的数据流规则：\n");
        sb.append("   - 主步骤的输出通过 {stepX.output} 传递给评估步骤。\n");
        sb.append("   - 评估步骤的输出通过 {step_evaluate.output} 传递给修正步骤。\n");
        sb.append("   - 修正步骤的输出应替换或更新原始主步骤的输出，下一轮循环时主步骤应引用修正后的内容。\n");
        sb.append("   - 如果存在多个循环闭环，前一个循环的最终输出通过占位符传递给后一个循环。\n");
        // 7. 循环终止
        sb.append("7. 循环终止条件（满足任一即退出）：\n");
        sb.append("   - 评分 >= qualityThreshold（达标）。\n");
        sb.append("   - 达到 maxIterations（超过最大迭代次数）。\n");
        sb.append("   - 用户在检查点选择终止。\n");
        sb.append("   达到最大迭代次数且未达标时，应根据 checkpointOnMaxIterations 决定是否插入确认检查点。\n\n");
        // 8. 循环和纠正适用不同模型
        sb.append("8. 循环中的评估步骤和修正步骤应尽量使用不同的模型以获得客观评价和有效修正。\n");
        sb.append("   - 评估步骤可指定 model 为 \"plusModel\"（更严格），修正步骤可指定 model 为 \"reasoningModel\"（更强）。\n");
        sb.append("   - 主步骤的 model 可省略，使用默认模型。\n");

        // ========= 竞争执行 (COMPETITIVE) 规则（完整版） =========
        sb.append("【竞争执行 (COMPETITIVE) 规则】\n");
        sb.append("当 executionMode 为 COMPETITIVE 时，必须遵循以下规则：\n");
        sb.append("1. 计划中必须包含 competitiveConfig，其中 groups 数组定义了一个或多个竞争组。\n\n");
        sb.append("2. 每个竞争组包含：\n");
        sb.append("   - groupId: 竞争组唯一标识（如 \"group_analysis\"）。\n");
        sb.append("   - competitors: 竞争者列表，每个竞争者包含 competitorId 和 stepIds（该竞争者执行的步骤 ID 列表，按顺序执行）。\n");
        sb.append("   - selectorStepId: 该组的评审步骤 ID（必须在 steps 数组中定义）。\n");
        sb.append("   - maxConcurrency（可选）: 最大并行竞争者数量。\n\n");
        sb.append("3. 竞争前的共享步骤：\n");
        sb.append("   - 如果所有竞争者需要基于相同的数据进行分析，应在竞争组之前放置一个共享的普通步骤（如数据搜集），\n");
        sb.append("     然后所有竞争者都引用该共享步骤的输出（通过 {step_collect.output}）。\n");
        sb.append("   - 不要在竞争组之前放置会被竞争者各自重复执行的步骤。\n\n");
        sb.append("4. 竞争者的步骤构成：\n");
        sb.append("   - 每个竞争者可以包含一个或多个步骤（stepIds 列表）。\n");
        sb.append("   - 竞争者内部步骤之间通过 dependsOn 声明执行顺序。\n");
        sb.append("   - 同一组内的不同竞争者必须使用不同的 LLM 模型（model 字段），以保证方案多样性。\n");
        sb.append("     模型必须从【可用大模型列表】中选择。\n");
        sb.append("   - 竞争者的第一个步骤的 dependsOn 应包含共享步骤的 ID（如果存在共享步骤）。\n\n");
        sb.append("5. 评审步骤的依赖声明（非常重要）：\n");
        sb.append("   - 评审步骤（selectorStepId）的 dependsOn 必须显式包含该组所有竞争者的最后步骤 ID。\n");
        sb.append("   - 如果竞争组中间有检查点（INTERRUPT 步骤），评审步骤的 dependsOn 也必须包含该检查点步骤 ID。\n");
        sb.append("   - 示例：竞争者A 的步骤为 [step_a1, step_a2]，竞争者B 的步骤为 [step_b1]，\n");
        sb.append("     竞争组中间有确认检查点 step_confirm，\n");
        sb.append("     则评审步骤 step_judge 的 dependsOn 应为 [\"step_a2\", \"step_b1\", \"step_confirm\"]。\n\n");
        sb.append("6. 评审步骤的输入（必须严格遵守）：\n");
        sb.append("   - 评审步骤的 input 中必须包含一个键名为 \"competitionResults\" 的字段。\n");
        sb.append("   - 该字段的值必须使用占位符 \"{groupId.output}\"，其中 groupId 必须与你在 competitiveConfig 中为该竞争组定义的实际 groupId 完全一致。\n");
        sb.append("   - 示例：若竞争组的 groupId 为 \"group_initial\"，则 input 中必须写：\n");
        sb.append("     \"competitionResults\": \"{group_initial.output}\"\n");
        sb.append("   - 执行引擎会在运行时自动将占位符替换为实际的聚合输出对象（格式如下方所述）。\n");
        sb.append("   - 严禁手动拼接 JSON 字符串，只允许使用占位符。\n\n");
        sb.append("   - 聚合输出是一个对象，结构为：\n");
        sb.append("     {\n");
        sb.append("       \"competitor_A\": {\"competitorId\": \"A\", \"output\": { \"content\": \"...\" }},\n");
        sb.append("       \"competitor_B\": {\"competitorId\": \"B\", \"output\": { \"content\": \"...\" }},\n");
        sb.append("       ...\n");
        sb.append("     }\n");
        sb.append("   - 该对象中的键名 \"competitor_A\"、\"competitor_B\" 等对应竞争者的 competitorId。\n");
        sb.append("   - 不要尝试引用单个竞争者的步骤输出（如 {step_analyze_1.output}），因为这些输出已被聚合到上述结构中。\n\n");
        sb.append("7. 多个竞争组按数组顺序依次执行，前一个组的评审结果（或优胜者输出）可以作为后一个组竞争者的输入。\n");
        sb.append("   后一个组的竞争者应引用前一个组的评审步骤输出（通过 {step_judge.output}）。\n\n");
        sb.append("8. 评审步骤的输出格式要求：\n");
        sb.append("   - 评审步骤的 task 必须明确要求以 JSON 格式输出，且必须包含以下字段：\n");
        sb.append("     · winnerId: 获胜竞争者标识（如 \"A\"）\n");
        sb.append("     · selectedOutput: 获胜者的完整输出内容（即该竞争者最终步骤的输出原文，不可省略）\n");
        sb.append("     · reason: 选择理由（简要说明）\n");
        sb.append("     · score: 评分（可选）\n");
        sb.append("   - 示例 task 描述：\"评审三份报告...以 JSON 格式输出：{\\\"winnerId\\\": \\\"A\\\", \\\"selectedOutput\\\": \\\"(获胜者的完整报告内容)\\\", \\\"reason\\\": \\\"...\\\", \\\"score\\\": 92}\"\n\n");
        sb.append("   - 注意：检查点（INTERRUPT 步骤）的 checkpoint.question 中不要包含占位符（如 {stepX.output.score}），应直接用自然语言描述问题。具体的报告内容可通过检查点步骤的 input 字段传递给前端展示，不需要在 question 中呈现。\n");

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

        // ========= 可用的子agent能力清单 =========
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

        // ========= 新增：可用大模型列表 =========
        sb.append("=== 可用大模型列表 ===\n");
        sb.append("当 type 为 LLM_CALL 时，必须指定 model 字段，值必须从以下模型中选择（不指定则使用默认模型）：\n");
        chatClientSelector.getAvailableModelNames().forEach(name -> sb.append("- ").append(name).append("\n"));
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
        sb.append("    - model: 当 type 为 LLM_CALL 时可选，必须从【可用大模型列表】中选择指定使用的模型名称（如 \"plusModelClient\", \"reasoningModelClient\", \"fastModelClient\"）。\n");
        sb.append("       在循环纠正模式中，评估步骤和修正步骤建议使用不同的模型（如评估用 \"reasoningModelClient\"，修正用 \"plusModelClient\"）。\n");
        sb.append("    - input: 对象，必须包含 'userQuery'（值为当前用户请求全文），以及步骤所需的其它参数。\n");
        sb.append("    - dependsOn: 字符串数组，列出本步骤依赖的前置步骤 id，无依赖则为空数组。\n");
        sb.append("    - checkpoint: 当 type 为 INTERRUPT 时可选，包含以下子字段：\n");
        sb.append("        - type: \"CREDENTIAL\" 或 \"CONFIRM\"\n");
        sb.append("        - question: 向用户展示的问题\n");
        sb.append("        - requiredScopes: 字符串数组（CREDENTIAL 类型必填）\n");
        sb.append("        - timeoutMinutes: 超时分钟数，默认 30\n\n");
        sb.append("    - conditionConfig: 当步骤为条件判断步骤时使用（通常与 type: LLM_CALL 配合），包含以下子字段：\n");
        sb.append("        - expression: 条件描述，如“判断销售趋势”\n");
        sb.append("        - evaluationMethod: \"LLM_JUDGE\" 或 \"SPEL\"\n");
        sb.append("        - branches: 对象，key 为分类标签，value 为目标步骤 ID\n");
        sb.append("        - defaultStepId: 默认分支的步骤 ID\n");
        sb.append("      注意：即使提供了 conditionConfig，也必须在 input 中包含 branches 和 default 字段。\n");
        sb.append("    - includeChatHistory: 布尔值，仅当 type 为 LLM_CALL 且需要对话历史时才设置为 true，否则可省略或设为 false。\n\n");
        sb.append("- correctionConfig: 当 executionMode 为 ITERATIVE_CORRECTION 时必须，包含以下子字段：\n");
        sb.append("    - loops: 数组，每个元素是一个循环闭环，包含：\n");
        sb.append("        - loopId: 循环的唯一标识（如 \"data_quality_loop\"）。\n");
        sb.append("        - firstStepId: 循环起始步骤的 ID（该步骤及其后续步骤、直到 evaluatorStepId 之前，构成循环主步骤序列）。\n");
        sb.append("        - evaluatorStepId: 评估步骤的 ID（类型为 LLM_CALL，需输出 JSON: {\\\"score\\\": 85, \\\"issues\\\": [...]}）。\n");
        sb.append("        - correctorStepId: 修正步骤的 ID（可选，无修正步骤则省略）。\n");
        sb.append("        - maxIterations: 最大迭代次数（如 3）。\n");
        sb.append("        - qualityThreshold: 质量阈值 0-100（如 85）。\n");
        sb.append("        - checkpointAfterEachIteration: 是否在每轮循环后插入确认检查点（可选，默认 false）。\n");
        sb.append("        - checkpointOnMaxIterations: 达到最大迭代次数后是否插入检查点（可选，默认 true）。\n");
        sb.append("    多个循环按数组顺序依次执行，前一个达标后自动进入下一个。\n\n");
        sb.append("- competitiveConfig: 当 executionMode 为 COMPETITIVE 时必须，包含以下子字段：\n");
        sb.append("    - groups: 数组，每个元素是一个竞争组，包含：\n");
        sb.append("        - groupId: 竞争组唯一标识（如 \"group_a\"）。\n");
        sb.append("        - competitors: 数组，每个竞争者包含：\n");
        sb.append("            - competitorId: 竞争者标识（如 \"1\", \"2\"）。\n");
        sb.append("            - stepIds: 该竞争者执行的步骤 ID 列表（按顺序执行）。\n");
        sb.append("        - selectorStepId: 该组的评审步骤 ID（步骤必须在 steps 数组中定义）。\n");
        sb.append("        - selectionCriteria（可选）: 评审选择标准描述。\n");
        sb.append("        - maxConcurrency（可选）: 最大并行竞争者数量。\n");
        sb.append("    - selectionCriteria（可选）: 全局默认评审标准。\n\n");
        sb.append("   - 注意：在竞争模式中，后续步骤（如优化步骤、最终文档生成）需要引用评审步骤输出的 selectedOutput 字段，使用占位符 {step_judge_xx.output.selectedOutput}。\n\n");

        // ========= 正确示例（覆盖五种执行模式） =========
        sb.append("【正确示例1：顺序执行（SEQUENTIAL）】\n");
        sb.append("用户请求：\"帮我分析销售数据并生成报告\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"model\": \"reasoningModelClient\",\n");
        sb.append("      \"task\": \"对销售数据进行多维分析，输出趋势图和关键指标\",\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析销售数据并生成报告\"},\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step2\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"根据分析结果撰写一份专业的销售报告\",\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析销售数据并生成报告\", \"analysisData\": \"{step1.output}\"},\n");
        sb.append("      \"dependsOn\": [\"step1\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("【正确示例2：并行执行（PARALLEL）】\n");
        sb.append("用户请求：\"同时搜集汽车信息、用户评价和保险政策\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"PARALLEL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"从网络上搜集最新的电动汽车信息\",\n");
        sb.append("      \"input\": {\"userQuery\": \"同时搜集汽车信息、用户评价和保险政策\"},\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step2\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"从汽车之家等平台搜集用户对主流电动车的口碑评价\",\n");
        sb.append("      \"input\": {\"userQuery\": \"同时搜集汽车信息、用户评价和保险政策\"},\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step3\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"搜集主流电动汽车的保险政策信息\",\n");
        sb.append("      \"input\": {\"userQuery\": \"同时搜集汽车信息、用户评价和保险政策\"},\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step4\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"model\": \"reasoningModelClient\",\n");
        sb.append("      \"task\": \"将汽车信息、口碑评价和保险政策进行汇总分析，生成综合报告\",\n");
        sb.append("      \"input\": {\"carInfo\": \"{step1.output}\", \"reviews\": \"{step2.output}\", \"insurance\": \"{step3.output}\", \"userQuery\": \"同时搜集汽车信息、用户评价和保险政策\"},\n");
        sb.append("      \"dependsOn\": [\"step1\", \"step2\", \"step3\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("【正确示例3：条件分支（CONDITIONAL）】\n");
        sb.append("用户请求：\"分析销售数据，如果下滑就生成应对方案，否则生成总结报告\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"CONDITIONAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step1\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"model\": \"reasoningModelClient\",\n");
        sb.append("      \"task\": \"分析最近的销售数据，判断整体趋势\",\n");
        sb.append("      \"input\": {\"userQuery\": \"分析销售数据，如果下滑就生成应对方案，否则生成总结报告\"},\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step_condition\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"model\": \"plusModelClient\",\n");
        sb.append("      \"task\": \"根据分析结果判断趋势，只输出一个词：下滑、平稳或增长\",\n");
        sb.append("      \"input\": {\n");
        sb.append("        \"analysisData\": \"{step1.output}\",\n");
        sb.append("        \"branches\": {\"下滑\": \"step_fix\", \"平稳\": \"step_summary\", \"增长\": \"step_expand\"},\n");
        sb.append("        \"default\": \"step_summary\"\n");
        sb.append("      },\n");
        sb.append("      \"conditionConfig\": {\n");
        sb.append("        \"expression\": \"判断销售趋势\",\n");
        sb.append("        \"evaluationMethod\": \"LLM_JUDGE\",\n");
        sb.append("        \"branches\": {\"下滑\": \"step_fix\", \"平稳\": \"step_summary\", \"增长\": \"step_expand\"},\n");
        sb.append("        \"defaultStepId\": \"step_summary\"\n");
        sb.append("      },\n");
        sb.append("      \"dependsOn\": [\"step1\"]\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step_fix\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"model\": \"reasoningModelClient\",\n");
        sb.append("      \"task\": \"根据下滑趋势生成详细应对方案\",\n");
        sb.append("      \"input\": {\"analysisData\": \"{step1.output}\", \"userQuery\": \"分析销售数据，如果下滑就生成应对方案，否则生成总结报告\"},\n");
        sb.append("      \"dependsOn\": [\"step_condition\"]\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step_summary\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"根据平稳或增长趋势生成总结报告\",\n");
        sb.append("      \"input\": {\"analysisData\": \"{step1.output}\", \"userQuery\": \"分析销售数据，如果下滑就生成应对方案，否则生成总结报告\"},\n");
        sb.append("      \"dependsOn\": [\"step_condition\"]\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step_expand\",\n");
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"task\": \"根据增长趋势生成扩张建议报告\",\n");
        sb.append("      \"input\": {\"analysisData\": \"{step1.output}\", \"userQuery\": \"分析销售数据，如果下滑就生成应对方案，否则生成总结报告\"},\n");
        sb.append("      \"dependsOn\": [\"step_condition\"]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("【正确示例4：包含检查点的敏感任务】\n");
        sb.append("用户请求：\"帮我分析财务数据并发送报告给老板\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"SEQUENTIAL\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"step_auth\",\n");
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
        sb.append("      \"type\": \"LLM_CALL\",\n");
        sb.append("      \"model\": \"reasoningModelClient\",\n");
        sb.append("      \"task\": \"提取并分析最近一个季度的财务数据，输出趋势图和关键指标\",\n");
        sb.append("      \"input\": {\"userQuery\": \"帮我分析财务数据并发送报告给老板\"},\n");
        sb.append("      \"dependsOn\": [\"step_auth\"]\n");
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

        sb.append("【正确示例5：多循环纠正（含检查点）】\n");
        sb.append("用户请求：\"生成一份高质量的市场分析报告。先确保搜集的数据足够全面准确，再确保报告分析深入、格式专业。每轮优化后让我确认是否继续。\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"ITERATIVE_CORRECTION\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\"id\": \"step_collect\", \"type\": \"LLM_CALL\", \"task\": \"从多个权威来源搜集最新市场数据\", \"input\": {\"userQuery\": \"...\"}, \"dependsOn\": []},\n");
        sb.append("    {\"id\": \"step_eval_data\", \"type\": \"LLM_CALL\", \"model\": \"plusModelClient\", \"task\": \"评估数据质量，输出 JSON: {\\\"score\\\": 85, \\\"issues\\\": [...]}\", \"input\": {\"data\": \"{step_collect.output}\"}, \"dependsOn\": [\"step_collect\"]},\n");
        sb.append("    {\"id\": \"step_fix_data\", \"type\": \"LLM_CALL\", \"model\": \"reasoningModelClient\", \"task\": \"根据评估反馈补充或修正数据\", \"input\": {\"data\": \"{step_collect.output}\", \"feedback\": \"{step_eval_data.output}\"}, \"dependsOn\": [\"step_eval_data\"]},\n");
        sb.append("    {\"id\": \"step_data_confirm\", \"type\": \"INTERRUPT\", \"task\": \"数据质量达标，确认是否继续\", \"checkpoint\": {\"type\": \"CONFIRM\", \"question\": \"数据质量已达标，是否继续生成报告？\"}, \"input\": {\"data\": \"{step_collect.output}\"}, \"dependsOn\": [\"step_fix_data\"]},\n");
        sb.append("    {\"id\": \"step_draft\", \"type\": \"LLM_CALL\", \"task\": \"基于数据撰写报告初稿\", \"input\": {\"data\": \"{step_collect.output}\", \"userQuery\": \"...\"}, \"dependsOn\": [\"step_data_confirm\"]},\n");
        sb.append("    {\"id\": \"step_eval_report\", \"type\": \"LLM_CALL\", \"model\": \"plusModelClient\", \"task\": \"评估报告质量，输出 JSON: {\\\"score\\\": 85, \\\"issues\\\": [...]}\", \"input\": {\"report\": \"{step_draft.output}\"}, \"dependsOn\": [\"step_draft\"]},\n");
        sb.append("    {\"id\": \"step_confirm_fix\", \"type\": \"INTERRUPT\", \"task\": \"确认是否执行修正\", \"checkpoint\": {\"type\": \"CONFIRM\", \"question\": \"报告质量未达标（当前评分：{step_eval_report.output.score}），是否执行自动修正？\"}, \"input\": {\"evaluation\": \"{step_eval_report.output}\"}, \"dependsOn\": [\"step_eval_report\"]},\n");
        sb.append("    {\"id\": \"step_fix_report\", \"type\": \"LLM_CALL\", \"model\": \"reasoningModelClient\", \"task\": \"根据评估反馈修正报告\", \"input\": {\"report\": \"{step_draft.output}\", \"feedback\": \"{step_eval_report.output}\"}, \"dependsOn\": [\"step_confirm_fix\"]},\n");
        sb.append("    {\"id\": \"step_final_confirm\", \"type\": \"INTERRUPT\", \"task\": \"确认最终报告\", \"checkpoint\": {\"type\": \"CONFIRM\", \"question\": \"报告已优化完成，是否满意？\"}, \"input\": {\"report\": \"{step_draft.output}\"}, \"dependsOn\": [\"step_fix_report\"]}\n");
        sb.append("  ],\n");
        sb.append("  \"correctionConfig\": {\n");
        sb.append("    \"loops\": [{\n");
        sb.append("      \"loopId\": \"data_quality_loop\",\n");
        sb.append("      \"firstStepId\": \"step_collect\",\n");
        sb.append("      \"evaluatorStepId\": \"step_eval_data\",\n");
        sb.append("      \"correctorStepId\": \"step_fix_data\",\n");
        sb.append("      \"maxIterations\": 2,\n");
        sb.append("      \"qualityThreshold\": 80,\n");
        sb.append("      \"checkpointAfterEachIteration\": false\n");
        sb.append("    }, {\n");
        sb.append("      \"loopId\": \"report_quality_loop\",\n");
        sb.append("      \"firstStepId\": \"step_draft\",\n");
        sb.append("      \"evaluatorStepId\": \"step_eval_report\",\n");
        sb.append("      \"correctorStepId\": \"step_fix_report\",\n");
        sb.append("      \"maxIterations\": 3,\n");
        sb.append("      \"qualityThreshold\": 85,\n");
        sb.append("      \"checkpointAfterEachIteration\": true\n");
        sb.append("    }]\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("【正确示例6：竞争执行（COMPETITIVE）】\n");
        sb.append("用户请求：\"生成一份市场分析报告，我需要多个不同的方案供选择，然后由评审模型选出最优的一份。\"\n");
        sb.append("输出 JSON：\n");
        sb.append("{\n");
        sb.append("  \"executionMode\": \"COMPETITIVE\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\"id\": \"step_a1\", \"type\": \"LLM_CALL\", \"model\": \"reasoningModelClient\", \"task\": \"竞争者1：搜集数据并生成详细分析报告\", \"input\": {\"userQuery\": \"...\"}, \"dependsOn\": []},\n");
        sb.append("    {\"id\": \"step_b1\", \"type\": \"LLM_CALL\", \"model\": \"plusModelClient\", \"task\": \"竞争者2：从不同角度搜集数据并生成简明报告\", \"input\": {\"userQuery\": \"...\"}, \"dependsOn\": []},\n");
        sb.append("    {\"id\": \"step_c1\", \"type\": \"LLM_CALL\", \"model\": \"fastModelClient\", \"task\": \"竞争者3：快速搜集关键数据并生成摘要报告\", \"input\": {\"userQuery\": \"...\"}, \"dependsOn\": []},\n");
        sb.append("    {\"id\": \"step_c2\", \"type\": \"A2A_DELEGATE\", \"agent\": \"document-agent\", \"task\": \"将竞争者3的报告格式化\", \"input\": {\"report\": \"{step_c1.output}\"}, \"dependsOn\": [\"step_c1\"]},\n");
        sb.append("    {\"id\": \"step_judge\", \"type\": \"LLM_CALL\", \"model\": \"reasoningModelClient\", \"task\": \"评审三份报告，选择数据最全面、分析最深入、格式最专业的一份\", \"input\": {\"competitionResults\": \"{group_report.output}\", \"userQuery\": \"...\"}, \"dependsOn\": [\"step_a1\", \"step_b1\", \"step_c2\"]}\n");
        sb.append("  ],\n");
        sb.append("  \"competitiveConfig\": {\n");
        sb.append("    \"groups\": [{\n");
        sb.append("      \"groupId\": \"group_report\",\n");
        sb.append("      \"competitors\": [\n");
        sb.append("        {\"competitorId\": \"A\", \"stepIds\": [\"step_a1\"]},\n");
        sb.append("        {\"competitorId\": \"B\", \"stepIds\": [\"step_b1\"]},\n");
        sb.append("        {\"competitorId\": \"C\", \"stepIds\": [\"step_c1\", \"step_c2\"]}\n");
        sb.append("      ],\n");
        sb.append("      \"selectorStepId\": \"step_judge\",\n");
        sb.append("      \"selectionCriteria\": \"数据全面、分析深入、格式专业\"\n");
        sb.append("    }]\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        // ========= 最终指令 =========
        sb.append("现在，根据以上所有信息，为当前用户请求生成一个 JSON 执行计划。\n");
        sb.append("⚠️ 注意：你必须直接输出纯 JSON 字符串，不要使用 ```json 代码块包裹，不要添加任何解释文字或 Markdown 标记。\n");
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
        sb.append("   - 依赖前序步骤的步骤，input 中必须使用 {stepX.output} 引用前序输出，不能为空。\n");
        sb.append("   - 在条件分支中，不同分支的步骤应正确引用条件步骤或更早步骤的输出。\n");
        sb.append("   - 在并行执行中，并行步骤之间不能有隐式数据依赖（必须通过 dependsOn 显式声明）。\n\n");
        sb.append("   - 在循环纠正中，评估步骤必须引用主步骤的输出，修正步骤必须引用评估反馈。\n\n");
        sb.append("   - 在竞争模式中，评审步骤的 input 必须引用竞争组的聚合输出 {groupId.output}。\n\n");

        sb.append("3. 执行模式与结构 (executionMode)：执行模式选择是否合理？步骤结构是否清晰？\n");
        sb.append("   - 若任务包含多个互不依赖的子任务，应使用 PARALLEL 模式；若强行使用 SEQUENTIAL，扣分。\n");
        sb.append("   - 若任务需要根据中间结果动态选择后续路径，必须使用 CONDITIONAL 模式，且条件步骤 (LLM_CALL) 的 task 必须明确要求输出分类标签（如“只输出一个词：下滑/平稳/增长”）。\n");
        sb.append("   - 若任务追求高质量输出且允许迭代优化，应使用 ITERATIVE_CORRECTION 模式。\n");
        sb.append("   - 若任务需要多方案择优，应使用 COMPETITIVE 模式。\n\n");

        sb.append("4. 循环纠正有效性 (correctionEffectiveness)：当使用 ITERATIVE_CORRECTION 模式时，循环配置是否完整且合理？\n");
        sb.append("   - 计划必须包含 correctionConfig，且其中 loops 数组不能为空。\n");
        sb.append("   - 每个循环必须指定 evaluatorStepId，且该步骤的 task 必须明确要求输出评分（如 JSON 格式包含 score 字段）。\n");
        sb.append("   - 评估步骤之前的步骤构成主步骤序列，它们之间的依赖关系必须清晰正确。\n");
        sb.append("   - 如果存在修正步骤（correctorStepId），其 input 必须引用评估步骤的输出（反馈）和需要修正的原始内容。\n");
        sb.append("   - maxIterations 和 qualityThreshold 必须合理（迭代次数建议 2-5 次，阈值建议 70-90）。\n");
        sb.append("   - 多个循环闭环之间的顺序和数据传递必须正确（前一个循环的最终输出应能传递给后一个循环）。\n\n");

        sb.append("5. 竞争设计合理性 (competitiveDesign)：当使用 COMPETITIVE 模式时，竞争配置是否完整且合理？\n");
        sb.append("   - 计划必须包含 competitiveConfig，且其中 groups 数组不能为空。\n");
        sb.append("   - 每个竞争组必须指定 selectorStepId（评审步骤），且评审步骤的 task 应描述如何比较竞争者输出并选出最优。\n");
        sb.append("   - 同一组内的竞争者必须使用不同的 LLM 模型（model 字段），以保证方案多样性。\n");
        sb.append("   - 每个竞争者可以包含一个或多个步骤（stepIds），步骤间的依赖关系必须正确。\n");
        sb.append("   - 多个竞争组之间的顺序和数据传递必须合理。\n\n");


        sb.append("6. 检查点合理性 (checkpoint)：高风险步骤或不可逆操作前是否合理插入了 INTERRUPT 检查点？\n");
        sb.append("   - 涉及敏感数据访问、发送邮件、扣款、发布内容等操作前，应有 CREDENTIAL 或 CONFIRM 检查点。\n");
        sb.append("   - 在 CONDITIONAL 模式中，如果某个分支包含高风险操作，检查点应只在该分支内出现，不影响其他分支。\n\n");
        sb.append("   - 在 ITERATIVE_CORRECTION 模式中，修正步骤之前、达到最大迭代次数后应合理插入检查点。\n\n");
        sb.append("   - 在 COMPETITIVE 模式中，竞争者的关键操作前或评审步骤之前可插入检查点。\n\n");

        sb.append("7. 步骤效率 (efficiency)：是否存在可以合并的冗余步骤？\n");
        sb.append("   - 多个连续的 LLM_CALL 步骤如果处理同一批数据，应考虑合并为一个步骤。\n");
        sb.append("   - 条件分支中，不同分支如果包含重复的步骤，应提取到条件判断之前或之后。\n\n");
        sb.append("   - 循环纠正中，主步骤序列不应包含与循环目标无关的步骤。\n\n");
        sb.append("   - 竞争模式中，竞争者的步骤数量应控制在必要范围内，避免过度冗余。\n\n");

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
            sb.append("    \"").append(label).append("\": {\"agentAccuracy\": 8, \"dataFlow\": 7, \"executionMode\": 8, \"correctionEffectiveness\": 8, \"competitiveDesign\": 8, \"checkpoint\": 9, \"efficiency\": 8}");
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
                        .filter(msg -> !"done".equals(msg.getContent()))   // 过滤无效消息
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
     * 构建竞争模式评审步骤的专用提示词
     */
    public String buildCompetitionJudgeStepPrompt(Step step, Map<String, Object> context, MemoryContext memoryCtx) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();

        // ========= 1. 任务描述 =========
        String resolvedTask = replacePlaceholders(step.getTask(), context);
        sb.append("【任务】\n").append(resolvedTask).append("\n\n");

        // ========= 2. 竞争者输出展示 =========
        if (step.getInput() != null && !step.getInput().isEmpty()) {
            Map<String, Object> resolvedInput = resolveInput(step.getInput(), context);
            Object competitionResults = resolvedInput.get("competitionResults");

            // 如果值是字符串且看起来像 JSON，尝试反序列化（兼容 LLM 直接内联 JSON 字符串的情况）
            if (competitionResults instanceof String str && (str.startsWith("{") || str.startsWith("["))) {
                try {
                    competitionResults = objectMapper.readValue(str, Map.class);
                } catch (Exception e) {
                    log.warn("无法将 competitionResults 从 JSON 字符串转换为 Map", e);
                }
            }

            if (competitionResults instanceof Map) {
                Map<String, Object> competitorsMap = (Map<String, Object>) competitionResults;
                sb.append("【竞争者方案对比】\n");
                int index = 1;
                for (Map.Entry<String, Object> compEntry : competitorsMap.entrySet()) {
                    Object compData = compEntry.getValue();
                    if (compData instanceof Map) {
                        Map<String, Object> comp = (Map<String, Object>) compData;
                        String competitorId = comp.getOrDefault("competitorId", index).toString();
                        Object output = comp.get("output");

                        sb.append("--- 竞争者 ").append(competitorId).append(" ---\n");
                        if (output instanceof Map) {
                            Object content = ((Map<?, ?>) output).get("content");
                            sb.append(content != null ? content.toString() : "（无内容）").append("\n\n");
                        } else {
                            sb.append(output != null ? output.toString() : "（无内容）").append("\n\n");
                        }
                    }
                    index++;
                }
            }
        }

        // ========= 3. 输出格式要求 =========
        String selectionCriteria = step.getInput() != null ?
                (String) step.getInput().getOrDefault("selectionCriteria", "综合最优") : "综合最优";
        sb.append("【评审要求】\n");
        sb.append("请根据以下标准进行评审：").append(selectionCriteria).append("\n");
        sb.append("你需要输出一个 JSON 对象，包含以下字段：\n");
        sb.append("- winnerId: 胜出竞争者的标识（如 A、B、C）\n");
        sb.append("- selectedOutput: 胜出者的完整输出内容，必须原样复制，不可省略或改写\n");
        sb.append("- reason: 选择该竞争者的简要理由\n");
        sb.append("- score: 综合评分（1-100 的整数）\n");
        sb.append("请严格按照上述字段输出 JSON，不要添加任何额外文字。\n");

        return sb.toString();
    }

    /**
     * 替换字符串中所有 {key} 占位符为上下文中的字符串值
     */
    public String replacePlaceholders(String template, Map<String, Object> runtimeContext) {
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String refKey = matcher.group(1);
            Object ctxValue = runtimeContext.get(refKey);
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
            if (str.length() > 100000) {
                return str.substring(0, 100000) + "\n...(内容过长，已截断，完整数据可引用占位符获取)";
            }
            return str;
        }
        if (value instanceof Map map) {
            String json = objectMapper.writeValueAsString(map);
            if (json.length() > 100000) {
                // 保留 map 中第一个 key 的完整内容，其余截断
                // 或者转为摘要字符串
                return json.substring(0, 100000) + "...(已截断)";
            }
            return value; // 如果 Map 不大，保持原样
        }
        return value;
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