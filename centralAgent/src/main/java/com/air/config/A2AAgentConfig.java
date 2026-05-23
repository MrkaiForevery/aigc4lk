package com.air.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class A2AAgentConfig {

    @Bean(name = "dataAnalysisAgent")
    public ReactAgent dataAnalysisAgent(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ReactAgent.builder()
                .name("data_analysis_agent")
                .model(chatModel)
                .description("专门用于数据分析和统计计算的本地智能体")
                .instruction("你是一个专业的数据分析专家，擅长处理各类数据统计和分析任务。" +
                        "你能够理解用户的数据分析需求，提供准确的统计计算结果和专业的分析建议。")
                .outputKey("messages")
                .build();
    }
}