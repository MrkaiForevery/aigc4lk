package com.air.commander.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SupportModeNameType {

    QWEN_TURBO("qwen","qwen-turbo","fastModel","fastModelClient"),
    QWEN_LONG("qwen","qwen-long","reasoningModel","reasoningModelClient"),
    QWEN_PLUS("qwen","qwen-plus","plusModel","plusModelClient");

    private final String platformName;
    private final String modelName;
    private final String relationChatModelBeanName;
    private final String relationChatClientBeanName;

}