package com.air.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CookingAgentConfig {

    @Bean(name = "cookingAgent")
    public ReactAgent cookingAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("cooking_agent")  // 必须与 card.name 一致
                .model(chatModel)
                .description("处理烹饪领域相关问题")
                .instruction("你是一个专业烹饪领域Agent，只能处理烹饪领域相关问题。")
                .outputKey("messages")
                .build();
    }

}