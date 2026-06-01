package com.air.commander.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "graph")
public class GraphTemplateConfig {

    private List<GraphTemplate> templates = new ArrayList<>();

    @Data
    public static class GraphTemplate {
        private String templateId;
        private String type;                 // SEQUENTIAL / PARALLEL / A2A_DELEGATE / CONDITIONAL
        private String description;
        private String targetAgent;          // A2A 目标
        private String taskType;             // A2A 任务类型
        private List<NodeConfig> nodes;      // 顺序节点
        private List<BranchConfig> parallelBranches;  // 并行分支
        private NodeConfig merge;            // 并行汇总
        private Map<String, Object> payloadMapping;   // A2A 负载映射
        private String conditionKey;         // 条件路由键
        private Map<String, RouteTarget> routes;      // 条件路由
    }

    @Data
    public static class NodeConfig {
        private String nodeId;
        private String type;                 // LLM_CALL / A2A_DELEGATE
        private String prompt;
        private String outputKey;
        private String targetAgent;
        private String taskType;
        private Map<String, Object> inputMapping;
    }

    @Data
    public static class BranchConfig {
        private String branchId;
        private String type;
        private String prompt;
        private String outputKey;
    }

    @Data
    public static class RouteTarget {
        private String templateId;
        private String type;
        private String prompt;
        private String outputKey;
    }
}