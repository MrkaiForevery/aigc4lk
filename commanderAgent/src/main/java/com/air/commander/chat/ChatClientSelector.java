package com.air.commander.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大模型chatClient模型选择器
 */
@Slf4j
@Component
public class ChatClientSelector {

    private final Map<String, ChatClient> clientMap;

    /**
     * Spring 自动将所有 ChatClient 类型的 Bean 注入到这个 Map 中
     * Key = Bean 名称（如 "fastModelClient", "reasoningModelClient"）
     * Value = ChatClient 实例
     */
    public ChatClientSelector(Map<String, ChatClient> clientMap) {
        this.clientMap = clientMap;
    }

    public  Map<String, ChatClient> getAllMap(){
        return this.clientMap;
    }

    /**
     * 根据 Bean 名称获取对应的 ChatClient
     * @param beanName Bean 名称，如 "reasoningModelClient"
     * @return ChatClient 实例，如果找不到则返回 null
     */
    public ChatClient getClient(String beanName) {
        return clientMap.get(beanName);
    }

    /**
     * 获取默认的 ChatClient（可自行定义默认值）
     */
    public ChatClient getDefaultClient() {
        // 优先返回 fastModelClient，如果不存在则返回 map 中的第一个
        return clientMap.getOrDefault("fastModelClient",
                clientMap.values().stream().findFirst().orElse(null));
    }

    /**
     * 获取所有可用模型名称（Bean名称），用于生成提示词时告知LLM可选模型。
     */
    public Set<String> getAvailableModelNames() {
        return clientMap.keySet();
    }

    /**
     * 根据部署类型的不同获取该类型下的所有可用模型名称（Bean名称），用于生成提示词时告知LLM可选模型。
     */
    public Set<String> getAvailableModelNamesByDeploymentType(SupportChatModeType.DeploymentType deploymentType) {
        Set<String> chatClientBeanNames = SupportChatModeType.getChatClientNameByDeploymentType(deploymentType);
        return clientMap.keySet().stream()
                .filter(chatClientBeanNames ::contains)
                .collect(Collectors.toSet());
    }
}
