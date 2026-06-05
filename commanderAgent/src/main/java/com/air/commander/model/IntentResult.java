package com.air.commander.model;

/**
 * 意图分析结果实体
 *
 * @param isTemplate
 * @param scenario
 * @param templateId
 * @param complexity
 */
public record IntentResult(boolean isTemplate, String scenario,
                           String templateId, int complexity) {

}