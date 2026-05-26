package com.air.commander.model;

import com.air.commander.config.ChatModelRoutingConfig;
import com.air.commander.config.CommanderMetaConfig;
import com.air.commander.entity.CommanderModelDefinition;
import com.air.commander.entity.IntentAnalysis;
import com.air.commander.entity.ModelSelection;
import com.air.platform.common.a2a.channel.CommanderChannel;
import com.air.platform.common.enums.ModelType;
import com.air.platform.common.model.ModelDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelRouter {

    private final CommanderMetaConfig commanderMetaConfig;
    private final ChatModelRoutingConfig chatModelRoutingConfig;

    /**
     * 选择最佳模型
     */
    public ModelSelection selectModel(IntentAnalysis intent,
                                      CommanderChannel.ArchitectureSelection architecture) {
        // 1. 根据复杂度选择模型ID
        String modelId = commanderMetaConfig.getComplexityModelMapping()
                .getOrDefault(intent.getComplexity(), "qwen-plus");

        // 2. 如果使用加权轮询策略
        if ("weighted-round-robin".equals(commanderMetaConfig.getModelSelectionStrategy())) {
            modelId = weightedRoundRobin(intent.getRequiredCapabilities());
        }

        // 3. 获取模型定义（从动态配置中查找）
        ModelDefinition definition = getModelDefinition(modelId);
        if (definition == null) {
            definition = getModelDefinition("qwen-turbo");
            if (definition == null) {
                throw new IllegalStateException("No fallback model definition found");
            }
        }

        return ModelSelection.builder()
                .modelId(definition.getModelId())
                .modelName(definition.getModelName())
                .provider(definition.getProvider())
                .selectionStrategy(commanderMetaConfig.getModelSelectionStrategy())
                .capabilities(definition.getCapabilities())
                .build();
    }

    /**
     * 加权轮询选择
     */
    private String weightedRoundRobin(List<String> requiredCapabilities) {
        List<ModelDefinition> candidates = getAvailableModels().stream()
                .filter(def -> requiredCapabilities == null || requiredCapabilities.isEmpty() ||
                        def.getCapabilities().stream().anyMatch(requiredCapabilities::contains))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return "qwen-turbo";
        }

        double totalWeight = candidates.stream().mapToDouble(ModelDefinition::getWeight).sum();
        double random = ThreadLocalRandom.current().nextDouble(totalWeight);

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
     * 根据 modelId 获取模型定义
     */
    public ModelDefinition getModelDefinition(String modelId) {
        return chatModelRoutingConfig.getModels().stream()
                .filter(def -> def.getModelId().equals(modelId) && def.isEnabled())
                .findFirst()
                .map(this::convertToPlatformModel)
                .orElse(null);
    }

    /**
     * 获取所有可用模型
     */
    public List<ModelDefinition> getAvailableModels() {
        return chatModelRoutingConfig.getModels().stream()
                .filter(CommanderModelDefinition::isEnabled)
                .map(this::convertToPlatformModel)
                .collect(Collectors.toList());
    }

    /**
     * 将 Nacos 配置中的 ModelDefinition 转换为platformCommon中通用 ModelDefinition
     */
    private ModelDefinition convertToPlatformModel(CommanderModelDefinition configDef) {
        return ModelDefinition.builder()
                .modelId(configDef.getModelId())
                .modelName(configDef.getModelName())
                .provider(configDef.getProvider())
                .type(ModelType.valueOf(configDef.getType()))  // 确保枚举值匹配
                .capabilities(configDef.getCapabilities())
                .weight(configDef.getWeight())
                .enabled(configDef.isEnabled())
                .build();
    }
}