package com.air.commander.model;

/**
 * 意图分析结果实体
 *
 * @param isTemplate
 * @param scenario
 * @param templateId
 * @param complexity
 */
public record IntentResult(boolean isTemplate,
                           String scenario,
                           String templateId,
                           int complexity,
                           boolean highRisk) {

    public static IntentResult template(String scenario, String templateId, int complexity, boolean highRisk) {
        return new IntentResult(true, scenario, templateId, complexity, highRisk);
    }

    public static IntentResult dynamic(int complexity, boolean highRisk) {
        return new IntentResult(false, null, null, complexity, highRisk);
    }
}