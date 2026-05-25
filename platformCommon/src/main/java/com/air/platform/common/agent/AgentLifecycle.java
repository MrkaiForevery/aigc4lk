package com.air.platform.common.agent;

import com.air.platform.common.a2a.channel.CommanderChannel;
import com.air.platform.common.a2a.protocol.A2AMessage;
import com.air.platform.common.a2a.protocol.A2AResponse;
import com.air.platform.common.multimodal.vo.MultimodalInput;
import com.air.platform.common.multimodal.vo.MultimodalOutput;
import com.air.platform.common.multimodal.vo.MultimodalRequest;
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