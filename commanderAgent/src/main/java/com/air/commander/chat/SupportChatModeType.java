package com.air.commander.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@AllArgsConstructor
public enum SupportChatModeType {

    QWEN_TURBO("qwen", "qwen-long-latest",
            "fastModel", "fastModelClient",
            "适合长文本理解和生成，支持中英文，云端服务，成本较高", DeploymentType.CLOUD),
    QWEN_LONG("qwen", "qwen-plus-latest",
            "reasoningModel", "reasoningModelClient",
            "推理模型，擅长复杂逻辑推理和代码生成，思考过程详细，云端服务，成本较高",DeploymentType.CLOUD),
    QWEN_PLUS("qwen", "glm-5",
            "plusModel", "plusModelClient",
            "推理模型，擅长复杂逻辑推理和代码生成，思考过程详细，云端服务，成本较高",DeploymentType.CLOUD ),
    QWEN_VOICE("qwen", "qwen-tts-realtime-latest",
            "qwenVoiceModel", "qwenVoiceModelClient",
            "语音模态专用处理大模型",DeploymentType.CLOUD ),

    LOCAL_OLLAMA_QWEN("local_ollama", "qwen3.5:4b",
            "localOllamaQwenModel", "localOllamaQwenModelClient",
            "适合长文本理解和生成，支持中英文，本地部署服务，成本低",DeploymentType.LOCAL  ),
    LOCAL_OLLAMA_deepseek("local_ollama", "deepseek-r1:7b",
            "localOllamaDeepseekModel", "localOllamaDeepseekModelClient",
            "推理模型，擅长复杂逻辑推理和代码生成，思考过程详细，本地部署服务,成本较低",DeploymentType.LOCAL  );

    private final String platformName;
    private final String modelName;
    private final String relationChatModelBeanName;
    private final String relationChatClientBeanName;
    private final String capabilityDescription;
    private final DeploymentType deploymentType;

    @Getter
    @AllArgsConstructor
    public enum DeploymentType {
        LOCAL, CLOUD
    }

    public static String getChatModelCapabilityDescription(String beanName){
        for (SupportChatModeType type : SupportChatModeType.values()) {
            if (type.getRelationChatClientBeanName().equals(beanName)) {
                return type.getCapabilityDescription();
            }
        }
        return "未知模型";
    }

    public static Set<String> getChatClientNameByDeploymentType(DeploymentType deploymentType){
        HashSet<String> set = new HashSet<>();
        for (SupportChatModeType type : SupportChatModeType.values()) {
            if (type.getDeploymentType().equals(deploymentType)) {
                set.add(type.getRelationChatClientBeanName());
            }
        }
        return set;
    }
}