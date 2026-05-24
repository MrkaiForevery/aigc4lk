package com.air.platform.core.agent;

import com.air.a2a.core.channel.CommanderChannel;
import com.air.a2a.core.protocol.A2AMessage;
import com.air.a2a.core.protocol.A2AResponse;
import com.air.multimodal.vo.MultimodalInput;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class DualChannelAgent extends BaseAgent {
    
    protected DualChannelAgent(Builder<?, ?> builder) {
        super(builder);
    }
    
    /**
     * Commander通道：接收全局任务
     */
    public abstract CommanderChannel.CommanderResponse handleCommanderTask(CommanderChannel.CommanderTask task);
    
    /**
     * A2A通道：处理其他Agent请求
     */
    public abstract A2AResponse handlePeerRequest(A2AMessage message);
    
    /**
     * 处理多模态输入
     */
    protected abstract A2AResponse handleMultimodalInput(A2AMessage message);
    
    /**
     * 处理模态转换请求
     */
    protected abstract A2AResponse handleModalityConversion(A2AMessage message);
    
    @Override
    public void onCommanderTask(CommanderChannel.CommanderTask task) {
        handleCommanderTask(task);
    }
    
    @Override
    public A2AResponse onPeerRequest(A2AMessage message) {
        return handlePeerRequest(message);
    }
    
    @Override
    public void onMultimodalInput(MultimodalInput input) {
        A2AMessage message = A2AMessage.builder()
            .payload(input)
            .build();
        handleMultimodalInput(message);
    }
    
    public static abstract class Builder<T extends DualChannelAgent, B extends Builder<T, B>> 
            extends BaseAgent.Builder<T, B> {
        
        @Override
        protected abstract B self();
        
        @Override
        public abstract T build();
    }
}