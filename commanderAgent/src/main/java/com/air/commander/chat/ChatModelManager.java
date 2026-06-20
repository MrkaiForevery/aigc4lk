package com.air.commander.chat;

import com.air.commander.configloader.loader.RemoteConfigLoader;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    //--------------------------超时设置注入-----------------------//
    @Bean("cloudRestClientBuilder")
    @Primary
    public RestClient.Builder cloudRestClientBuilder() {
        // 创建一个底层 HttpRequestFactory，设置超时时间（单位：毫秒）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);       // 连接超时 10秒
        requestFactory.setReadTimeout(Duration.ofMinutes(7).toMillisPart()); // 读取超时 7分钟（根据模型调整）

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    @Bean("a2aRestClientBuilder")
    public RestClient.Builder a2aRestClientBuilder() {
        // 创建一个底层 HttpRequestFactory，设置超时时间（单位：毫秒）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);       // 连接超时 10秒
        requestFactory.setReadTimeout(Duration.ofMinutes(10).toMillisPart()); // 读取超时 10分钟（根据模型调整）

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    @Bean("localRestClientBuilder")
    public RestClient.Builder localRestClientBuilder() {
        // 创建一个底层 HttpRequestFactory，设置超时时间（单位：毫秒）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);       // 连接超时 10秒
        requestFactory.setReadTimeout(Duration.ofMinutes(3).toMillisPart()); // 读取超时 3分钟（根据模型调整）

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    //--------------------------云端大模型注入-----------------------//
    @Bean
    public DashScopeApi dashScopeApi(@Qualifier("cloudRestClientBuilder") RestClient.Builder cloudRestClientBuilder) {
        return DashScopeApi.builder()
                .apiKey(configLoader.getChatModelApiKey(QWEN))
                .restClientBuilder(cloudRestClientBuilder)
                .build();
    }

    @Bean("fastModel")
    public DashScopeChatModel fastModel(DashScopeApi api) {
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(SupportChatModeType.QWEN_FAST.getModelName())
                        .multiModel(true)
                        .temperature(0.3)
                        .topP(0.7)
                        .enableThinking(false)
                        .enableSearch(false)
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
                        .model(SupportChatModeType.MAIN_REASONING_MODEL.getModelName())
                        .temperature(0.2)
                        .enableThinking(false)
                        .topP(0.5)
                        .enableSearch(false)
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
                        .model(SupportChatModeType.QWEN_PLUS.getModelName())
                        .multiModel(true)
                        .temperature(0.3)
                        .enableThinking(false)
                        .topP(0.5)
                        .enableSearch(false)
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
                        .model(SupportChatModeType.QWEN_VOICE.getModelName())
                        .multiModel(true)
                        .temperature(0.8)
                        .topP(0.8)
                        .enableThinking(false)
                        .build())
                .build();
    }

    @Bean("qwenVoiceModelClient")
    public ChatClient qwenVoiceModelClient(@Qualifier("qwenVoiceModel") ChatModel qwenVoiceModel) {
        return ChatClient.builder(qwenVoiceModel).build();
    }

    //--------------------------本地大模型注入-----------------------//

    @Bean
    public OllamaApi localOllamaApi(@Qualifier("localRestClientBuilder") RestClient.Builder localRestClientBuilder) {

        return OllamaApi.builder()
                .baseUrl("http://localhost:11434")
                .restClientBuilder(localRestClientBuilder)
                .build();
    }

    @Bean("localOllamaQWEN3Model")
    public OllamaChatModel localOllamaQWEN3Model(OllamaApi localOllamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(localOllamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model(SupportChatModeType.LOCAL_OLLAMA_QWEN3.getModelName())
                        .temperature(0.7)
                        .build())
                .build();
    }

    @Bean("localOllamaQWEN3ModelClient")
    public ChatClient localOllamaQWEN3ModelClient(@Qualifier("localOllamaQWEN3Model") OllamaChatModel localOllamaQWEN3Model) {
        return ChatClient.builder(localOllamaQWEN3Model).build();
    }


    @Bean("localOllamaLLAMA3Model")
    public OllamaChatModel localOllamaLLAMA3Model(OllamaApi localOllamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(localOllamaApi)
                .defaultOptions(OllamaChatOptions.builder()
                        .model(SupportChatModeType.LOCAL_OLLAMA_LLAMA3.getModelName())
                        .temperature(0.7)
                        .build())
                .build();
    }

    @Bean("localOllamaLLAMA3ModelClient")
    public ChatClient localOllamaLLAMA3ModelClient(@Qualifier("localOllamaLLAMA3Model") OllamaChatModel localOllamaLLAMA3Model) {
        return ChatClient.builder(localOllamaLLAMA3Model).build();
    }
}