package com.air.commander.graph;

import com.air.commander.config.CommanderMetaConfig;
import com.air.commander.entity.IntentAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GraphTemplateSelector {

    private final CommanderMetaConfig commanderMetaConfig;

    /**
     * 根据意图分析结果选择最佳的 Graph 模板
     */
    public String selectTemplate(IntentAnalysis intent) {
        String scenario = intent.getScenario();
        String complexity = intent.getComplexity();
        String modality = intent.getModality();

        // 1. 精确匹配：场景 → 模板
        Map<String, String> scenarioMapping = commanderMetaConfig.getScenarioTemplateMapping();
        if (scenarioMapping != null && scenarioMapping.containsKey(scenario)) {
            String templateId = scenarioMapping.get(scenario);
            log.info("Graph template selected by scenario [{}] → {}", scenario, templateId);
            return templateId;
        }

        // 2. 多模态检测
        if (!"TEXT".equals(modality)) {
            log.info("Graph template selected by modality [{}] → multimodal-report", modality);
            return "multimodal-report";
        }

        // 3. 复杂度匹配
        Map<String, String> complexityMapping = commanderMetaConfig.getComplexityTemplateMapping();
        if (complexityMapping != null && complexityMapping.containsKey(complexity)) {
            String templateId = complexityMapping.get(complexity);
            log.info("Graph template selected by complexity [{}] → {}", complexity, templateId);
            return templateId;
        }

        // 4. 默认兜底
        log.info("Graph template fallback to default: sequential-pipeline");
        return "sequential-pipeline";
    }
}