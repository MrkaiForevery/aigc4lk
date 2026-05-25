package com.air.commander.architecture;

import com.air.commander.config.CommanderMetaConfig;
import com.air.commander.entity.IntentAnalysis;
import com.air.platform.common.a2a.channel.CommanderChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArchitectureSelector {

    private final CommanderMetaConfig CommanderMetaConfig;

    /**
     * 选择合适的架构
     */
    public CommanderChannel.ArchitectureSelection selectArchitecture(IntentAnalysis intent) {
        String scenario = intent.getScenario();
        String selectionReason;
        String architectureId;

        // 1. 首先尝试配置的默认映射
        architectureId = CommanderMetaConfig.getScenarioArchitectureMapping().get(scenario);
        
        if (architectureId != null) {
            selectionReason = "配置映射: " + scenario + " -> " + architectureId;
            log.info("Architecture selected by config mapping: {} -> {}", scenario, architectureId);
        } else {
            // 2. 多模态任务使用顺序流水线
            if (!"TEXT".equals(intent.getModality())) {
                architectureId = "sequential-pipeline";
                selectionReason = "多模态任务使用顺序流水线";
            } else {
                // 3. 根据复杂度选择
                architectureId = selectByComplexity(intent.getComplexity());
                selectionReason = "复杂度匹配: " + intent.getComplexity();
            }
        }

        return CommanderChannel.ArchitectureSelection.builder()
            .architectureId(architectureId)
            .architectureType(determineType(architectureId))
            .selectionReason(selectionReason)
            .scenario(scenario)
            .complexity(intent.getComplexity())
            .build();
    }

    /**
     * 根据复杂度选择架构
     */
    private String selectByComplexity(String complexity) {
        return switch (complexity) {
            case "HIGH" -> "debate-system";       // 复杂任务用辩论
            case "MEDIUM" -> "parallel-analysis"; // 中等任务用并行
            default -> "sequential-pipeline";      // 简单任务用顺序
        };
    }

    /**
     * 确定架构类型
     */
    private String determineType(String architectureId) {
        return switch (architectureId) {
            case "sequential-pipeline", "multimodal-analysis-pipeline" -> "SEQUENTIAL";
            case "parallel-analysis" -> "PARALLEL";
            case "smart-routing" -> "LLM_ROUTING";
            case "debate-system" -> "CUSTOM_GRAPH";
            default -> "SEQUENTIAL";
        };
    }
}