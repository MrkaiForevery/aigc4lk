package com.air.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CommanderAgentConfig {

    private final ChatModel chatModel;
    private final AgentCardProvider agentCardProvider;

    @Bean
    public ReactAgent commanderAgent() {
        A2aRemoteAgent remoteCookingAgent = A2aRemoteAgent.builder()
                .name("cooking_agent")
                .description("烹饪专家代理")
                .agentCardProvider(agentCardProvider)
                .build();
        ToolCallback cookingTool = FunctionToolCallback.builder(
                        "cooking_agent", input -> {
                            try {
                                return remoteCookingAgent.invoke(input.toString()).map(state -> state.value("messages").toString()).orElse("调用失败");
                            } catch (GraphRunnerException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .description("烹饪领域专家，处理烹饪相关问题")
                .inputType(String.class) // Agent 的输入类型通常是 String
                .toolCallResultConverter((result, request) -> result.toString()) // 直接返回结果
                .build();
        List<ToolCallback> agentTools = List.of(cookingTool);

        return ReactAgent.builder()
                .name("commander_agent")
                .description("调度Agent,充当指挥家角色，具备动态感知与智能路由能力")
                .instruction("""
                        你是中央调度Agent，负责分析用户需求，将任务分配给最合适的子Agent。
                        === 当前可用的子Agent及其能力清单 ===
                        - cooking_agent: 处理烹饪方面的问题
                        
                        === 调度规则 ===
                        1. 仔细分析用户需求，与每个Agent的能力进行匹配。
                        2. 将任务分配给最匹配的Agent（通过调用对应的工具）。
                        3. 如果多个Agent都合适，选择最擅长处理该任务的Agent。
                        4. 如果没有合适的Agent，直接告知用户当前无法处理。
                        """)
                .model(chatModel)
                .tools(agentTools)
                .build();
    }
}