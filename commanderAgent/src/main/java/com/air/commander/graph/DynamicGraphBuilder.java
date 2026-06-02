package com.air.commander.graph;

import com.air.commander.config.GraphTemplateConfig;
import com.air.commander.config.GraphTemplateConfig.*;
import com.air.platform.common.a2a.enums.A2AMessageType;
import com.air.platform.common.a2a.protocol.A2AMessage;
import com.air.platform.common.a2a.protocol.A2AResponse;
import com.air.platform.common.a2a.router.NacosA2ARouter;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicGraphBuilder {

    private final Map<String, ChatModel> modelCache;
    private final NacosA2ARouter a2aRouter;
    private final GraphTemplateConfig templateConfig;


    // ==================== 对外api ====================
    /**
     * 根据模板 ID 动态构建 StateGraph
     */
    public StateGraph buildFromTemplate(String templateId) throws GraphStateException {
        GraphTemplate template = findTemplate(templateId);
        log.info("Building graph from template: {} (type: {})", templateId, template.getType());
        return switch (template.getType()) {
            case "SEQUENTIAL" -> buildSequentialGraph(template);
            case "PARALLEL" -> buildParallelGraph(template);
            case "A2A_DELEGATE" -> buildA2ADelegateGraph(template);
            case "CONDITIONAL" -> buildConditionalGraph(template);
            default -> throw new UnsupportedOperationException("Unknown type: " + template.getType());
        };
    }

    /**
     * 统一的流式执行入口(伪装的流式)
     * 内部同步执行 StateGraph，然后将结果拆分为字符流
     */
    public Flux<String> compileAndExecuteStream(String templateId, Map<String, Object> input) {
        GraphTemplate template = findTemplate(templateId);
        // 其他模板：同步执行 StateGraph，提取最终文本后拆分
        return Flux.defer(() -> {
            try {
                Map<String, Object> result = compileAndExecute(templateId, input);
                String text = extractFinalText(template, result);
                return Flux.fromArray(text.split("(?<=\\G.{1})"));
            } catch (GraphStateException e) {
                return Flux.error(e);
            }
        });
    }

    /**
     * 编译并执行 Graph，返回结果 Map
     */
    public Map<String, Object> compileAndExecute(String templateId, Map<String, Object> input) throws GraphStateException {
        StateGraph graph = buildFromTemplate(templateId);
        CompiledGraph compiledGraph = graph.compile(CompileConfig.builder().build());
        Optional<OverAllState> result = compiledGraph.invoke(input);
        return result.map(OverAllState::data).orElse(Map.of("error", "Graph execution failed"));
    }

    // ==================== 构建方法 ====================
    private StateGraph buildSequentialGraph(GraphTemplate template) throws GraphStateException {
        KeyStrategyFactory keyStrategy = buildKeyStrategy(template);
        StateGraph graph = new StateGraph(template.getTemplateId(), keyStrategy);
        NodeConfig previous = null;
        for (NodeConfig node : template.getNodes()) {
            graph.addNode(node.getNodeId(), node_async(state -> executeNode(node, state)));
            if (previous == null) {
                graph.addEdge(START, node.getNodeId());
            } else {
                graph.addEdge(previous.getNodeId(), node.getNodeId());
            }
            previous = node;
        }
        if (previous != null) graph.addEdge(previous.getNodeId(), END);
        return graph;
    }

    private StateGraph buildParallelGraph(GraphTemplate template) throws GraphStateException {
        KeyStrategyFactory keyStrategy = buildKeyStrategy(template);
        StateGraph graph = new StateGraph(template.getTemplateId(), keyStrategy);
        for (BranchConfig branch : template.getParallelBranches()) {
            graph.addNode(branch.getBranchId(), node_async(state -> executeBranch(branch, state)));
            graph.addEdge(START, branch.getBranchId());
        }
        if (template.getMerge() != null) {
            graph.addNode("merge", node_async(state -> executeMerge(template.getMerge(), state)));
            for (BranchConfig branch : template.getParallelBranches()) {
                graph.addEdge(branch.getBranchId(), "merge");
            }
            graph.addEdge("merge", END);
        }
        return graph;
    }

    private StateGraph buildA2ADelegateGraph(GraphTemplate template) throws GraphStateException {
        KeyStrategyFactory keyStrategy = () -> Map.of(
                "query", new ReplaceStrategy(), "a2a_result", new ReplaceStrategy(),
                "user_id", new ReplaceStrategy(), "session_id", new ReplaceStrategy(),
                "image_base64", new ReplaceStrategy(), "audio_base64", new ReplaceStrategy()
        );
        StateGraph graph = new StateGraph(template.getTemplateId(), keyStrategy);
        graph.addNode("a2a_delegate", node_async(state -> {
            // 从 State 中获取 threadId
            String threadId = state.value("thread_id").orElse("default").toString();

            Map<String, Object> payload = resolvePayload(template.getPayloadMapping(), state);

            //把业务参数扁平化，不再嵌套在 "payload" 中
            Map<String, Object> a2aPayload = new HashMap<>();
            a2aPayload.put("taskType", template.getTaskType());
            a2aPayload.put("threadId", threadId);
            a2aPayload.putAll(payload);  // 直接把 topic, userId, sessionId 等放到顶层

            A2AResponse response = a2aRouter.routeMessage(A2AMessage.builder()
                    .senderAgentId("commander-agent")
                    .receiverAgentId(template.getTargetAgent())
                    .messageType(A2AMessageType.TASK_DELEGATION)
                    .payload(a2aPayload )
                    .build());
            return response.isSuccess() ? Map.of("a2a_result", response.getPayload())
                    : Map.of("error", response.getErrorMessage());
        }));
        graph.addEdge(START, "a2a_delegate").addEdge("a2a_delegate", END);
        return graph;
    }

    private StateGraph buildConditionalGraph(GraphTemplate template) throws GraphStateException {
        KeyStrategyFactory keyStrategy = () -> {
            Map<String, KeyStrategy> map = new HashMap<>();
            map.put("query", new ReplaceStrategy());
            map.put("complexity", new ReplaceStrategy());
            map.put("result", new ReplaceStrategy());
            return map;
        };
        StateGraph graph = new StateGraph(template.getTemplateId(), keyStrategy);
        graph.addNode("condition_check", node_async(state -> Map.of()));

        String defaultRoute = null;
        for (Map.Entry<String, RouteTarget> entry : template.getRoutes().entrySet()) {
            String routeKey = entry.getKey();
            RouteTarget target = entry.getValue();
            String nodePrefix = "route_" + routeKey + "_";

            if (target.getTemplateId() != null) {
                addTemplateToGraph(graph, target.getTemplateId(), "condition_check", END, nodePrefix);
            } else {
                String llmNode = nodePrefix + "llm";
                graph.addNode(llmNode, node_async(state -> executeRouteLLM(target, state)));
                graph.addConditionalEdges("condition_check",
                        edge_async(state -> routeKey), Map.of(routeKey, llmNode));
                graph.addEdge(llmNode, END);
            }
            if (defaultRoute == null) defaultRoute = routeKey;
        }

        graph.addEdge(START, "condition_check");
        String finalDefault = defaultRoute;
        graph.addConditionalEdges("condition_check",
                edge_async(state -> {
                    String val = state.value(template.getConditionKey()).orElse("MEDIUM").toString();
                    return template.getRoutes().containsKey(val) ? val : finalDefault;
                }),
                template.getRoutes().keySet().stream()
                        .collect(HashMap::new, (m, k) -> m.put(k, k), HashMap::putAll));
        return graph;
    }

    // ==================== 递归模板合并 ====================

    private void addTemplateToGraph(StateGraph graph, String templateId,
                                    String inputNodeId, String outputNodeId, String nodePrefix) throws GraphStateException {
        GraphTemplate template = findTemplate(templateId);
        switch (template.getType()) {
            case "SEQUENTIAL" -> addSequentialToGraph(graph, template, inputNodeId, outputNodeId, nodePrefix);
            case "PARALLEL" -> addParallelToGraph(graph, template, inputNodeId, outputNodeId, nodePrefix);
            case "A2A_DELEGATE" -> {
                String nodeId = nodePrefix + "a2a";
                graph.addNode(nodeId, node_async(state -> executeA2ANodeFromTemplate(template, state)));
                graph.addEdge(inputNodeId, nodeId);
                graph.addEdge(nodeId, outputNodeId);
            }
            case "CONDITIONAL" -> {
                String condNodeId = nodePrefix + "cond_check";
                graph.addNode(condNodeId, node_async(state -> Map.of()));
                graph.addEdge(inputNodeId, condNodeId);
                for (Map.Entry<String, RouteTarget> entry : template.getRoutes().entrySet()) {
                    String routeKey = entry.getKey();
                    RouteTarget target = entry.getValue();
                    String childPrefix = nodePrefix + routeKey + "_";
                    if (target.getTemplateId() != null) {
                        addTemplateToGraph(graph, target.getTemplateId(), condNodeId, outputNodeId, childPrefix);
                    } else {
                        String llmNode = childPrefix + "llm";
                        graph.addNode(llmNode, node_async(state -> executeRouteLLM(target, state)));
                        graph.addConditionalEdges(condNodeId,
                                edge_async(state -> routeKey), Map.of(routeKey, llmNode));
                        graph.addEdge(llmNode, outputNodeId);
                    }
                }
                graph.addEdge(condNodeId, outputNodeId); // 默认兜底
            }
        }
    }

    private void addSequentialToGraph(StateGraph graph, GraphTemplate template,
                                      String inputNodeId, String outputNodeId, String prefix) throws GraphStateException {
        List<NodeConfig> nodes = template.getNodes();
        if (nodes.isEmpty()) return;
        NodeConfig first = nodes.get(0);
        NodeConfig last = nodes.get(nodes.size() - 1);
        NodeConfig prev = null;
        for (NodeConfig node : nodes) {
            String nodeId = prefix + node.getNodeId();
            graph.addNode(nodeId, node_async(state -> executeNode(node, state)));
            if (prev == null) {
                graph.addEdge(inputNodeId, nodeId);
            } else {
                graph.addEdge(prefix + prev.getNodeId(), nodeId);
            }
            prev = node;
        }
        graph.addEdge(prefix + last.getNodeId(), outputNodeId);
    }

    private void addParallelToGraph(StateGraph graph, GraphTemplate template,
                                    String inputNodeId, String outputNodeId, String prefix) throws GraphStateException {
        List<BranchConfig> branches = template.getParallelBranches();
        if (branches.isEmpty()) return;
        for (BranchConfig branch : branches) {
            String nodeId = prefix + branch.getBranchId();
            graph.addNode(nodeId, node_async(state -> executeBranch(branch, state)));
            graph.addEdge(inputNodeId, nodeId);
        }
        if (template.getMerge() != null) {
            String mergeId = prefix + "merge";
            graph.addNode(mergeId, node_async(state -> executeMerge(template.getMerge(), state)));
            for (BranchConfig branch : branches) {
                graph.addEdge(prefix + branch.getBranchId(), mergeId);
            }
            graph.addEdge(mergeId, outputNodeId);
        } else {
            for (BranchConfig branch : branches) {
                graph.addEdge(prefix + branch.getBranchId(), outputNodeId);
            }
        }
    }

    // ==================== 节点执行方法 ====================

    private Map<String, Object> executeNode(NodeConfig node, OverAllState state) {
        return switch (node.getType()) {
            case "LLM_CALL" -> executeLLMNode(node, state);
            case "A2A_DELEGATE" -> executeA2ANode(node, state);
            default -> Map.of(node.getOutputKey(), "Unknown node type: " + node.getType());
        };
    }

    private Map<String, Object> executeLLMNode(NodeConfig node, OverAllState state) {
        ChatModel model = getModelFromState(state);
        ChatClient client = ChatClient.builder(model).build();
        String result = client.prompt()
                .user(userMessage -> resolvePrompt(node.getPrompt(), state, userMessage))
                .call().content();
        return Map.of(node.getOutputKey(), result);
    }

    private Map<String, Object> executeA2ANode(NodeConfig node, OverAllState state) {
        Map<String, Object> payload = resolvePayload(node.getInputMapping(), state);
        A2AResponse response = a2aRouter.routeMessage(A2AMessage.builder()
                .senderAgentId("commander-agent")
                .receiverAgentId(node.getTargetAgent())
                .messageType(A2AMessageType.TASK_DELEGATION)
                .payload(Map.of("taskType", node.getTaskType(), "payload", payload))
                .build());
        return response.isSuccess() ? Map.of(node.getOutputKey(), response.getPayload())
                : Map.of("error", response.getErrorMessage());
    }

    private Map<String, Object> executeBranch(BranchConfig branch, OverAllState state) {
        ChatModel model = getModelFromState(state);
        ChatClient client = ChatClient.builder(model).build();
        String result = client.prompt()
                .user(userMessage -> userMessage.text(branch.getPrompt())
                        .param("query", state.value("query").orElse("").toString()))
                .call().content();
        return Map.of(branch.getOutputKey(), result);
    }

    private Map<String, Object> executeMerge(NodeConfig merge, OverAllState state) {
        return executeLLMNode(merge, state);
    }

    private Map<String, Object> executeRouteLLM(RouteTarget target, OverAllState state) {
        ChatModel model = getModelFromState(state);
        ChatClient client = ChatClient.builder(model).build();
        String result = client.prompt()
                .user(userMessage -> userMessage.text(target.getPrompt())
                        .param("query", state.value("query").orElse("").toString()))
                .call().content();
        return Map.of(target.getOutputKey(), result);
    }

    private Map<String, Object> executeA2ANodeFromTemplate(GraphTemplate template, OverAllState state) {
        Map<String, Object> payload = resolvePayload(template.getPayloadMapping(), state);
        A2AResponse response = a2aRouter.routeMessage(A2AMessage.builder()
                .senderAgentId("commander-agent")
                .receiverAgentId(template.getTargetAgent())
                .messageType(A2AMessageType.TASK_DELEGATION)
                .payload(Map.of("taskType", template.getTaskType(), "payload", payload))
                .build());
        return response.isSuccess() ? Map.of("a2a_result", response.getPayload())
                : Map.of("error", response.getErrorMessage());
    }

    // ==================== 工具方法 ====================
    private GraphTemplate findTemplate(String templateId) {
        return templateConfig.getTemplates().stream()
                .filter(t -> t.getTemplateId().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
    }

    private Map<String, Object> resolvePayload(Map<String, Object> mapping, OverAllState state) {
        Map<String, Object> payload = new HashMap<>();
        if (mapping == null) return payload;
        mapping.forEach((key, value) -> {
            if (value instanceof String str && str.startsWith("{") && str.endsWith("}")) {
                String stateKey = str.substring(1, str.length() - 1);
                payload.put(key, state.value(stateKey).orElse(""));
            } else {
                payload.put(key, value);
            }
        });
        return payload;
    }

    private void resolvePrompt(String prompt, OverAllState state, ChatClient.PromptUserSpec userMessage) {
        userMessage.text(prompt);
        state.data().forEach((key, value) -> userMessage.param(key, value != null ? value : ""));
    }

    private KeyStrategyFactory buildKeyStrategy(GraphTemplate template) {
        return () -> {
            Map<String, KeyStrategy> map = new HashMap<>();
            map.put("query", new ReplaceStrategy());
            map.put("model_id", new ReplaceStrategy());
            map.put("complexity", new ReplaceStrategy());
            if (template.getNodes() != null) {
                for (NodeConfig node : template.getNodes()) {
                    map.put(node.getOutputKey(), new ReplaceStrategy());
                }
            }
            if (template.getParallelBranches() != null) {
                for (BranchConfig branch : template.getParallelBranches()) {
                    map.put(branch.getOutputKey(), new ReplaceStrategy());
                }
            }
            if (template.getMerge() != null) {
                map.put(template.getMerge().getOutputKey(), new ReplaceStrategy());
            }
            return map;
        };
    }

    private ChatModel getModelFromState(OverAllState state) {
        String modelId = state.value("model_id").orElse("qwen-turbo").toString();
        return modelCache.computeIfAbsent(modelId, id -> modelCache.getOrDefault("qwen-turbo",
                modelCache.values().stream().findFirst().orElse(null)));
    }

    private Map<String, Object> resolvePayloadFromMap(Map<String, Object> mapping, Map<String, Object> stateMap) {
        Map<String, Object> payload = new HashMap<>();
        if (mapping == null) return payload;
        mapping.forEach((key, value) -> {
            if (value instanceof String str && str.startsWith("{") && str.endsWith("}")) {
                String stateKey = str.substring(1, str.length() - 1);
                payload.put(key, stateMap.getOrDefault(stateKey, ""));
            } else {
                payload.put(key, value);
            }
        });
        return payload;
    }


    /**
     * todo 这个涉及多模态的转化，需要定义一个统一的响应response提取工具类，要慢点来
     * 从同步执行结果中提取最终文本，这里只是提取文本
     */
    private String extractFinalText(GraphTemplate template, Map<String, Object> result) {
        // 优先取最后一个节点的 outputKey
        if ("SEQUENTIAL".equals(template.getType()) && template.getNodes() != null) {
            String lastOutputKey = template.getNodes().get(template.getNodes().size() - 1).getOutputKey();
            return result.getOrDefault(lastOutputKey, "").toString();
        }
        if ("PARALLEL".equals(template.getType()) && template.getMerge() != null) {
            return result.getOrDefault(template.getMerge().getOutputKey(), "").toString();
        }

        // A2A委托模板：从 a2a_result 的 SSE 流中解析
        if ("A2A_DELEGATE".equals(template.getType())) {
            Object a2aResult = result.get("a2a_result");
            if (a2aResult instanceof String rawSSEData) {
                String document = a2aRouter.extractFinalDocument(rawSSEData);
                if (document != null && !document.isBlank()) {
                    return document;
                }
            }
        }

        // 默认整个结果转字符串
        return result.toString();
    }
}