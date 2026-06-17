package com.air.commander.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SupportChatModeType {

    QWEN_TURBO("qwen","qwen-long-latest","fastModel","fastModelClient"),
    QWEN_LONG("qwen","qwen-plus-latest","reasoningModel","reasoningModelClient"),
    QWEN_PLUS("qwen","glm-5","plusModel","plusModelClient");

    private final String platformName;
    private final String modelName;
    private final String relationChatModelBeanName;
    private final String relationChatClientBeanName;

}