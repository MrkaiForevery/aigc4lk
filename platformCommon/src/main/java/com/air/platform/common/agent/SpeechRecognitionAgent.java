package com.air.platform.common.agent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SpeechRecognitionAgent extends DualChannelAgent {
    protected SpeechRecognitionAgent(Builder<?, ?> builder) {
        super(builder);
    }
}








