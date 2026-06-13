package com.air.commander.conversation.contract;

import com.air.commander.model.ExecutionResult;
import com.air.commander.model.Step;
import com.air.commander.model.StepDataContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据契约引擎：统一处理上下文数据的提取、格式化、注入
 * 
 * 核心职责：
 * 1. 根据步骤的 InputContract 自动从全局上下文中提取所需数据
 * 2. 自动进行截断、格式化
 * 3. 自动将步骤输出注册到全局上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataContractEngine {

    private final ObjectMapper objectMapper;
    
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    // ==================== 输入处理 ====================

    /**
     * 根据步骤的数据契约，从全局上下文中自动构建输入参数
     * 
     * @param step          当前步骤
     * @param globalContext 全局上下文（包含所有已执行步骤的输出）
     * @return 构建好的输入参数 Map
     */
    public Map<String, Object> buildInput(Step step, Map<String, Object> globalContext) {
        StepDataContract contract = step.getDataContract();
        Map<String, Object> input = new HashMap<>();

        // 1. 如果步骤有明确的 input 字段（LLM 生成的），优先使用
        if (step.getInput() != null && !step.getInput().isEmpty()) {
            for (Map.Entry<String, Object> entry : step.getInput().entrySet()) {
                String key = entry.getKey();
                Object rawValue = resolvePlaceholder(entry.getValue(), globalContext);
                input.put(key, truncateIfNeeded(rawValue, getMaxInputSize(contract)));
            }
        }

        // 2. 如果定义了数据契约，根据契约提取数据（补充或覆盖）
        if (contract != null && contract.getInputFields() != null) {
            for (StepDataContract.InputField field : contract.getInputFields()) {
                String alias = field.getAlias() != null ? field.getAlias() : field.getName();
                
                // 从全局上下文中查找数据
                Object value = findInContext(globalContext, field.getName());
                
                if (value != null) {
                    input.put(alias, truncateIfNeeded(value, field.getMaxLength() > 0 ? field.getMaxLength() : getMaxInputSize(contract)));
                } else if (field.isRequired()) {
                    log.warn("必填字段缺失: {}, 步骤: {}", field.getName(), step.getId());
                    input.put(alias, field.getDefaultValue() != null ? field.getDefaultValue() : "（数据缺失）");
                } else if (field.getDefaultValue() != null) {
                    input.put(alias, field.getDefaultValue());
                }
            }
        }

        // 3. 自动注入 userQuery（从全局上下文中获取）
        if (!input.containsKey("userQuery") && globalContext.containsKey("userQuery")) {
            input.put("userQuery", globalContext.get("userQuery"));
        }

        return input;
    }

    // ==================== 输出处理 ====================

    /**
     * 将步骤的执行结果自动注册到全局上下文
     * 
     * @param step          当前步骤
     * @param result        执行结果
     * @param globalContext 全局上下文（会被修改）
     */
    public void publishOutput(Step step, ExecutionResult result, Map<String, Object> globalContext) {
        // 1. 默认注册：stepId.output
        String defaultKey = step.getId() + ".output";
        if (result.isSuccess() && result.getOutput() != null) {
            globalContext.put(defaultKey, result.getOutput());
        }

        // 2. 如果定义了数据契约，按契约注册
        StepDataContract contract = step.getDataContract();
        if (contract != null && contract.getOutputField() != null) {
            String outputKey = contract.getOutputField().getName();
            if (result.isSuccess() && result.getOutput() != null) {
                globalContext.put(outputKey, result.getOutput());
            }
        }

        // 3. 始终注册原始输出（供后续步骤引用）
        if (result.getOutput() != null) {
            globalContext.put(defaultKey, result.getOutput());
        }
    }

    // ==================== 失败策略 ====================

    /**
     * 根据步骤的数据契约中的失败策略，决定如何处理失败
     */
    public StepDataContract.FailurePolicy getFailurePolicy(Step step) {
        StepDataContract contract = step.getDataContract();
        if (contract != null && contract.getOnFailure() != null) {
            return contract.getOnFailure();
        }
        // 默认：必选步骤回滚并停止，非必选步骤标记为失败
        return step.isMandatory() ? 
            StepDataContract.FailurePolicy.ROLLBACK_AND_STOP : 
            StepDataContract.FailurePolicy.MARK_AS_FAILED;
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析占位符 {key}
     */
    private Object resolvePlaceholder(Object value, Map<String, Object> context) {
        if (value instanceof String str) {
            // 完整匹配 {xxx}
            if (str.matches("\\{[^}]+\\}")) {
                String key = str.substring(1, str.length() - 1);
                return context.getOrDefault(key, str);
            }
            // 部分包含占位符
            if (str.contains("{")) {
                return replaceAllPlaceholders(str, context);
            }
        } else if (value instanceof Map) {
            Map<String, Object> resolved = new HashMap<>();
            ((Map<String, Object>) value).forEach((k, v) -> resolved.put(k, resolvePlaceholder(v, context)));
            return resolved;
        }
        return value;
    }

    /**
     * 替换字符串中所有占位符
     */
    private String replaceAllPlaceholders(String template, Map<String, Object> context) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object replacement = context.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 在全局上下文中递归查找 key（支持 "stepX.output.content" 这样的路径）
     */
    private Object findInContext(Map<String, Object> context, String key) {
        String[] parts = key.split("\\.");
        Object current = context;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 截断过长的值
     */
    private Object truncateIfNeeded(Object value, int maxLength) {
        if (maxLength <= 0) maxLength = 100000;
        
        if (value instanceof String str) {
            if (str.length() > maxLength) {
                return str.substring(0, maxLength) + "\n...(内容过长已截断)";
            }
            return str;
        }
        
        if (value instanceof Map) {
            try {
                String json = objectMapper.writeValueAsString(value);
                if (json.length() > maxLength) {
                    // 对于 Map，尝试提取 content 字段
                    Object content = ((Map<?, ?>) value).get("content");
                    if (content instanceof String str) {
                        return truncateIfNeeded(str, maxLength);
                    }
                    return json.substring(0, maxLength) + "\n...(内容过长已截断)";
                }
                return value;
            } catch (Exception e) {
                return value;
            }
        }
        
        return value;
    }

    private int getMaxInputSize(StepDataContract contract) {
        return contract != null && contract.getMaxInputSize() > 0 ? 
            contract.getMaxInputSize() : 100000;
    }
}