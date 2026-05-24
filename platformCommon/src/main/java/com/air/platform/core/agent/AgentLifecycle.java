package com.air.platform.core.agent;

import com.air.a2a.core.channel.CommanderChannel;
import com.air.a2a.core.protocol.A2AMessage;
import com.air.a2a.core.protocol.A2AResponse;
import com.air.multimodal.vo.MultimodalInput;
import com.air.multimodal.vo.MultimodalOutput;
import com.air.multimodal.vo.MultimodalRequest;
import com.alibaba.cloud.nacos.registry.NacosRegistration;

public interface AgentLifecycle {
    
    void onRegister(NacosRegistration registration);
    
    void onModelBind(Object model);
    
    void onCommanderTask(CommanderChannel.CommanderTask task);
    
    A2AResponse onPeerRequest(A2AMessage message);
    
    void onBroadcast(A2AMessage message);
    
    void onMultimodalInput(MultimodalInput input);
    
    MultimodalOutput onMultimodalGenerate(MultimodalRequest request);
    
    void onContextSync(A2AMessage message);
    
    void onMemorySave(String threadId);
    
    void onError(Throwable error);
    
    void onRecover(String checkpointId);
    
    void onDeregister();
}