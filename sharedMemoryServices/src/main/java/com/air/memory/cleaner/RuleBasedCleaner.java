package com.air.memory.cleaner;

import com.air.memory.desensitizer.MemoryDesensitizer;
import com.air.memory.entity.CleanedMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 规则清洗器 —— 基于规则的低成本清洗
 * 适用于：去噪、去重标记、敏感信息脱敏、格式化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedCleaner {

    // 注入脱敏工具
    private final MemoryDesensitizer desensitizer;

    //todo 后期这些配置放到Nacos上面读取
    // 手机号正则
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    // 邮箱正则
    private static final Pattern EMAIL = Pattern.compile("[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}");
    // 身份证正则
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    // URL 正则
    private static final Pattern URL = Pattern.compile("https?://[\\w./?=&%#-]+");
    // 纯表情/特殊符号
    private static final Pattern EMOJI = Pattern.compile("[\\p{So}\\p{Cn}]");
    // 连续重复字符（如 "哈哈哈哈哈哈"）
    private static final Pattern REPEATED_CHAR = Pattern.compile("(.)\\1{4,}");

    /**
     * 规则清洗主入口
     */
    public CleanedMemory clean(String rawContent, String memoryType) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return buildInvalidResult(rawContent, memoryType, "内容为空");
        }

        String content = rawContent.trim();

        // 1. 长度检查：过短的内容无效
        if (content.length() < 3) {
            return buildInvalidResult(rawContent, memoryType, "内容过短（<3字符）");
        }

        // 2. 纯表情/符号检查
        String noEmoji = EMOJI.matcher(content).replaceAll("");
        if (noEmoji.trim().isEmpty()) {
            return buildInvalidResult(rawContent, memoryType, "纯表情/符号，无实质内容");
        }

        // 3. 重复字符检查（如 "哈哈哈哈哈哈"）
        if (REPEATED_CHAR.matcher(content).find()) {
            content = REPEATED_CHAR.matcher(content).replaceAll("$1$1$1");
        }

        // 4. 敏感信息脱敏
       content = desensitizer.desensitize(content);

        // 5. 截断：超过 800 字符的内容只保留前 500 + "..."
        String summary;
        if (content.length() > 800) {
            summary = content.substring(0, 500) + "...";
        } else {
            summary = content;
        }

        // 6. URL 替换（可选：保留或移除）
        // content = URL.matcher(content).replaceAll("[链接]");

        return CleanedMemory.builder()
                .rawContent(rawContent)
                .valid(true)
                .summary(summary)
                .knowledge(null)          // 规则层不提取知识
                .profileTags(null)        // 规则层不提取标签
                .memoryType(memoryType)
                .confidence(0.8)          // 规则层默认置信度
                .cleanedAt(System.currentTimeMillis())
                .cleanSource("RULE_BASED")
                .remark(null)
                .build();
    }

    /**
     * 批量规则清洗
     */
    public List<CleanedMemory> batchClean(List<String> rawContents, String memoryType) {
        return rawContents.stream()
                .map(content -> clean(content, memoryType))
                .filter(CleanedMemory::isValid)
                .toList();
    }

    /**
     * 构建无效结果
     */
    private CleanedMemory buildInvalidResult(String rawContent, String memoryType, String reason) {
        return CleanedMemory.builder()
                .rawContent(rawContent)
                .valid(false)
                .summary(null)
                .memoryType(memoryType)
                .confidence(0.0)
                .cleanedAt(System.currentTimeMillis())
                .cleanSource("RULE_BASED")
                .remark(reason)
                .build();
    }
}