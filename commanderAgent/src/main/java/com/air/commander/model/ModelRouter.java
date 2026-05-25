package com.air.commander.model;

import com.air.commander.config.CommanderMetaConfig;
import com.air.commander.entity.IntentAnalysis;
import com.air.commander.entity.ModelSelection;
import com.air.platform.common.a2a.channel.CommanderChannel;
import com.air.platform.common.enums.ModelType;
import com.air.platform.common.model.ModelDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelRouter {

    private final CommanderMetaConfig CommanderMetaConfig;
    
    /**
     * 模型定义缓存
     */
    private final Map<String, ModelDefinition> modelDefinitions = new ConcurrentHashMap<>();
    
    /**
     * 初始化默认模型定义
     */
    {
        modelDefinitions.put("qwen-max", ModelDefinition.builder()
            .modelId("qwen-max")
            .modelName("qwen-max")
            .provider("alibaba")
            .type(ModelType.DASHSCOPE)
            .capabilities(List.of("REASONING", "CODING", "ANALYSIS", "LONG_CONTEXT"))
            .weight(10)
            .enabled(true)
            .build());
        
        modelDefinitions.put("qwen-plus", ModelDefinition.builder()
            .modelId("qwen-plus")
            .modelName("qwen-plus")
            .provider("alibaba")
            .type(ModelType.DASHSCOPE)
            .capabilities(List.of("CHAT", "SUMMARIZATION", "FAST_RESPONSE"))
            .weight(8)
            .enabled(true)
            .build());
        
        modelDefinitions.put("deepseek-v3", ModelDefinition.builder()
            .modelId("deepseek-v3")
            .modelName("deepseek-chat")
            .provider("deepseek")
            .type(ModelType.OPENAI_COMPATIBLE)
            .capabilities(List.of("REASONING", "CODING", "LONG_CONTEXT"))
            .weight(9)
            .enabled(true)
            .build());
        
        modelDefinitions.put("qwen-turbo", ModelDefinition.builder()
            .modelId("qwen-turbo")
            .modelName("qwen-turbo")
            .provider("alibaba")
            .type(ModelType.DASHSCOPE)
            .capabilities(List.of("FAST_RESPONSE", "SIMPLE_CHAT", "INTENT_CLASSIFICATION"))
            .weight(5)
            .enabled(true)
            .build());
    }

    /**
     * 选择最佳模型
     */
    public ModelSelection selectModel(IntentAnalysis intent, CommanderChannel.ArchitectureSelection architecture) {
        // 1. 根据复杂度选择模型ID
        String modelId = CommanderMetaConfig.getComplexityModelMapping()
            .getOrDefault(intent.getComplexity(), "qwen-plus");
        
        // 2. 如果使用加权轮询策略
        if ("weighted-round-robin".equals(CommanderMetaConfig.getModelSelectionStrategy())) {
            modelId = weightedRoundRobin(intent.getRequiredCapabilities());
        }
        
        // 3. 获取模型定义
        ModelDefinition definition = modelDefinitions.getOrDefault(modelId, 
            modelDefinitions.get("qwen-turbo"));
        
        return ModelSelection.builder()
            .modelId(definition.getModelId())
            .modelName(definition.getModelName())
            .provider(definition.getProvider())
            .selectionStrategy(CommanderMetaConfig.getModelSelectionStrategy())
            .capabilities(definition.getCapabilities())
            .build();
    }

    /**
     * 加权轮询选择
     */
    private String weightedRoundRobin(List<String> requiredCapabilities) {
        List<ModelDefinition> candidates = modelDefinitions.values().stream()
            .filter(ModelDefinition::isEnabled)
            .filter(def -> requiredCapabilities == null || requiredCapabilities.isEmpty() ||
                def.getCapabilities().stream().anyMatch(requiredCapabilities::contains))
            .toList();
        
        if (candidates.isEmpty()) {
            return "qwen-turbo";
        }
        
        int totalWeight = candidates.stream().mapToInt(ModelDefinition::getWeight).sum();
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        
        int cumulative = 0;
        for (ModelDefinition candidate : candidates) {
            cumulative += candidate.getWeight();
            if (random < cumulative) {
                return candidate.getModelId();
            }
        }
        
        return candidates.get(0).getModelId();
    }

    /**
     * 获取模型定义
     */
    public ModelDefinition getModelDefinition(String modelId) {
        return modelDefinitions.get(modelId);
    }

    /**
     * 获取所有可用模型
     */
    public List<ModelDefinition> getAvailableModels() {
        return modelDefinitions.values().stream()
            .filter(ModelDefinition::isEnabled)
            .toList();
    }
}