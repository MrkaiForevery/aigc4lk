package com.air.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommanderLLMApiConfig {

//    @Bean
//    DashScopeApi dashScopeApi(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
//        return DashScopeApi.builder().apiKey(apiKey).build();
//    }
//
//    @Bean(name = "dashscopeChatModel")
//    ChatModel dashscopeChatModel(DashScopeApi dashScopeApi) {
//        return DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
//    }

}