package com.air.commander.chat;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * chatModel 模型注入
 */
@Slf4j
@Configuration
public class ChatModelManager {

    private final RemoteConfigLoader configLoader;
    private static final String QWEN = "qwen";

    // 手动构造器，只注入配置属性类
    public ChatModelManager(RemoteConfigLoader loader) {
        this.configLoader = loader;
    }


    //--------------------------云端大模型注入-----------------------//
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(configLoader.getChatModelApiKey(QWEN))
                .build();
    }

    @Bean("fastModel")
    public DashScopeChatModel fastModel(DashScopeApi api) {
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(SupportChatModeType.QWEN_FAST.getModelName())
                        .withTemperature(0.5)
                        .build())
                .build();
    }

    @Bean("fastModelClient")
    public ChatClient fastModelClient(@Qualifier("fastModel") ChatModel fastModel) {
        return ChatClient.builder(fastModel).build();
    }

    @Bean("reasoningModel")
    public DashScopeChatModel reasoningModel(DashScopeApi api) {
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(SupportChatModeType.MAIN_REASONING_MODEL.getModelName())
                        .withTemperature(0.3)
                        .build())
                .build();
    }

    @Bean("reasoningModelClient")
    public ChatClient reasoningModelClient(@Qualifier("reasoningModel") ChatModel reasoningModel) {
        return ChatClient.builder(reasoningModel).build();
    }

    @Bean("plusModel")
    public DashScopeChatModel plusModel(DashScopeApi api) {
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(SupportChatModeType.QWEN_PLUS.getModelName())
                        .withTemperature(0.4)
                        .build())
                .build();
    }

    @Bean("plusModelClient")
    public ChatClient plusModelClient(@Qualifier("plusModel") ChatModel reasoningModel) {
        return ChatClient.builder(reasoningModel).build();
    }


    @Bean("qwenVoiceModel")
    public DashScopeChatModel qwenVoiceModel(DashScopeApi api) {
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(SupportChatModeType.QWEN_VOICE.getModelName())
                        .withTemperature(0.3)
                        .build())
                .build();
    }

    @Bean("qwenVoiceModelClient")
    public ChatClient qwenVoiceModelClient(@Qualifier("qwenVoiceModel") ChatModel qwenVoiceModel) {
        return ChatClient.builder(qwenVoiceModel).build();
    }

    //--------------------------本地大模型注入-----------------------//

    @Bean
    public OllamaApi localOllamaApi() {
        return OllamaApi.builder()
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Bean("localOllamaQwen3Model")
    public OllamaChatModel localOllamaQwen3Model(OllamaApi localOllamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(localOllamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model(SupportChatModeType.LOCAL_OLLAMA_QWEN3.getModelName())
                        .temperature(0.3)
                        .build())
                .build();
    }

    @Bean("localOllamaQwen3ModelClient")
    public ChatClient localOllamaQwen3ModelClient(@Qualifier("localOllamaQwen3Model") OllamaChatModel localOllamaQwen3Model) {
        return ChatClient.builder(localOllamaQwen3Model).build();
    }


    @Bean("localOllamaQwen2Model")
    public OllamaChatModel localOllamaQwen2Model(OllamaApi localOllamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(localOllamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model(SupportChatModeType.LOCAL_OLLAMA_QWEN2.getModelName())
                        .temperature(0.3)
                        .build())
                .build();
    }

    @Bean("localOllamaDeepseekModelClient")
    public ChatClient localOllamaQwen2ModelClient(@Qualifier("localOllamaQwen2Model") OllamaChatModel localOllamaQwen2Model) {
        return ChatClient.builder(localOllamaQwen2Model).build();
    }
}