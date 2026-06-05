package com.air.commander.orchestrator;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 严格场景下的执行模板构建器
 */
@Component
@RequiredArgsConstructor
public class TemplateExecutor {

    private final RemoteConfigLoader configLoader;

    public OrchestrationPlan loadAndPersonalize(String templateId, String userInput, MemoryContext memoryCtx) {
        OrchestrationPlan template = configLoader.getTemplates().get(templateId);
        if (template == null) throw new RuntimeException("Template not found: " + templateId);
        // 可根据 memoryCtx 调整模板，这里略 todo
        return template;
    }
}