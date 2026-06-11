package com.air.commander.configloader.loader;

import com.air.commander.configloader.properties.*;
import com.air.commander.model.OrchestrationPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 从远程nacos上读取所有配置，提供统一的读取入口
 */
@Component
@RequiredArgsConstructor
public class RemoteConfigLoader {

    private final ChatModelApiKeyProperties chatModelApiKeyProperties;
    private final ChromaDbClientProperties chromaDbClientProperties;
    private final GraphTemplatesProperties graphProperties;
    private final IntentRulesProperties intentProperties;
    private final LoggingProperties loggingProperties;

    /**
     * 获取对应连接llm-chatModel的apiKey
     */
    public String getChatModelApiKey(String key){
       return chatModelApiKeyProperties.getApiKey().get(key);
    }

    /**
     * 获取ChromaDb连接配置参数
     */
    public ChromaDbClientProperties getChromaDbClientProperties(){
        return this.chromaDbClientProperties;
    }


    public List<GraphTemplatesProperties.TemplateItem> getGraphTemplates() {
        return graphProperties.getTemplates();
    }

    /**
     * 获取严格场景模式下所有执行编排模板（键为 templateId）
     */
    public Map<String, OrchestrationPlan> getTemplates() {
        return graphProperties.getTemplates().stream()
                .collect(Collectors.toMap(
                        GraphTemplatesProperties.TemplateItem::getTemplateId,
                        graphProperties::toPlan
                ));
    }

    /**
     * 场景标识 -> templateId 映射
     */
    public Map<String, String> getScenarioToTemplateId() {
        Map<String, String> map = new HashMap<>();
        for (var item : graphProperties.getTemplates()) {
            if (item.getTriggerScenarios() != null) {
                item.getTriggerScenarios().forEach(s -> map.put(s, item.getTemplateId()));
            }
            if (item.getTriggerKeywords() != null) {
                item.getTriggerKeywords().forEach(k -> map.put(k, item.getTemplateId()));
            }
        }
        return map;
    }

    /**
     * 模板模式-意图规则列表配置
     */
    public List<IntentRule> getIntentRules() {
        return intentProperties.getRules().stream()
                .map(r -> new IntentRule(r.getScenario(), r.getKeywords(), r.getComplexity(),r.isHighRisk()))
                .collect(Collectors.toList());
    }

    // 也可以直接返回属性对象，但为了最小改动，封装成原来的类型
    public record IntentRule(String scenario, List<String> keywords, int complexity,boolean highRisk) {
        public boolean matches(String input) {
            return keywords != null && keywords.stream().anyMatch(input::contains);
        }
    }

    /**
     * 获取对应日志配置参数
     */
    public LoggingProperties getLoggingProperties(String key){
        return this.loggingProperties;
    }

}