package com.air.commander.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
@AllArgsConstructor
public enum SupportChatModeType {

    QWEN_FAST("qwen", "qwen3.5-35b-a3b",
            "fastModel", "fastModelClient",
            "云端通义千问35B，综合能力强，适合长文本理解、复杂生成、一般推理任务，非极速模型", DeploymentType.CLOUD),
    MAIN_REASONING_MODEL("qwen", "deepseek-v3.1",
            "reasoningModel", "reasoningModelClient",
            "云端deepseek-v3.1推理模型，671B MoE，长思维链，逻辑严密，适合高难度多步推理、需求分析、方案评估，云端旗舰", DeploymentType.CLOUD),
    QWEN_PLUS("qwen", "qwen3.6-35b-a3b",
            "plusModel", "plusModelClient",
            "云端通义千问增强型，推理与速度均衡，适合中等复杂度任务、步骤规划、方案对比", DeploymentType.CLOUD),

    QWEN_VOICE("qwen", "qwen-tts-latest",
            "qwenVoiceModel", "qwenVoiceModelClient",
            "云端语音合成专用模型，支持实时TTS，适合将文本转为自然语音输出", DeploymentType.CLOUD),

    LOCAL_OLLAMA_QWEN3("local_ollama", "qwen3.5:0.8b",
            "localOllamaQWEN3Model", "localOllamaQWEN3ModelClient",
            "本地轻量对话模型0.8B，GPU加速，擅长于中文场景，响应极快，适合意图识别、简单应答、轻量生成，成本低", DeploymentType.LOCAL),
    LOCAL_OLLAMA_LLAMA3("local_ollama", "llama3.2:1b",
            "localOllamaLLAMA3Model", "localOllamaLLAMA3ModelClient",
            "本地轻量对话模型1B，GPU加速，擅长于英文场景，适合简单分类、关键词提取、快速兜底", DeploymentType.LOCAL);

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

    public static String getChatModelCapabilityDescription(String beanName) {
        for (SupportChatModeType type : SupportChatModeType.values()) {
            if (type.getRelationChatClientBeanName().equals(beanName)) {
                return type.getCapabilityDescription();
            }
        }
        return "未知模型";
    }

    public static Set<String> getChatClientNameByDeploymentType(DeploymentType deploymentType) {
        HashSet<String> set = new HashSet<>();
        for (SupportChatModeType type : SupportChatModeType.values()) {
            if (type.getDeploymentType().equals(deploymentType)) {
                set.add(type.getRelationChatClientBeanName());
            }
        }
        return set;
    }
}