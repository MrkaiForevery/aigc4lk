package com.air.commander.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.HashMap;
import java.util.Map;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "platform.commander")
public class CommanderMetaConfig {

    /** 意图分类使用的模型 */
    private String intentClassificationModel = "qwen-turbo";
    /** 默认架构 */
    private String defaultArchitecture = "sequential-pipeline";
    /** 架构选择策略：hybrid / rule / llm */
    private String architectureSelectionStrategy = "hybrid";
    /** 模型选择策略：weighted-round-robin / complexity-match */
    private String modelSelectionStrategy = "weighted-round-robin";
    /** 是否启用降级 */
    private boolean enableFallback = true;
    /** 最大重试次数 */
    private int maxRetries = 2;
    /** 超时时间（秒） */
    private int timeoutSeconds = 300;
    /** 是否启用多模态 */
    private boolean multimodalEnabled = true;

    /**
     * 复杂度到模型的映射（用于模型路由器）
     */
    private Map<String, String> complexityModelMapping = new HashMap<>();

    // ========== 新增：动态 Graph 编排相关 ==========
    /**
     * 场景到 Graph 模板的映射
     * 根据 IntentClassifier 输出的 scenario 选择对应的 Graph 模板 ID
     */
    private Map<String, String> scenarioTemplateMapping = new HashMap<>();

    /**
     * 复杂度到 Graph 模板的映射（兜底）
     * 当 scenario 没有精确匹配时，根据 complexity 选择模板
     */
    private Map<String, String> complexityTemplateMapping = new HashMap<>();
}