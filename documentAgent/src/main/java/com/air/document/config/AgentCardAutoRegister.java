package com.air.document.config;

import com.air.document.skills.DynamicAgentCardProvider;
import com.alibaba.cloud.ai.a2a.registry.nacos.service.NacosA2aOperationService;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentCardAutoRegister implements ApplicationRunner {

    private final DynamicAgentCardProvider dynamicAgentCardProvider;
    private final NacosA2aOperationService nacosA2aOperationService ;

    public AgentCardAutoRegister(DynamicAgentCardProvider dynamicAgentCardProvider,
                                 NacosA2aOperationService nacosA2aOperationService) {
        this.dynamicAgentCardProvider = dynamicAgentCardProvider;
        this.nacosA2aOperationService = nacosA2aOperationService;
    }


    @Override
    public void run(ApplicationArguments args) {
        AgentCardWrapper wrapper = dynamicAgentCardProvider.getAgentCard();
        if (wrapper != null && wrapper.getAgentCard() != null) {
            nacosA2aOperationService.registerAgent(wrapper.getAgentCard());
            log.info("Dynamic AgentCard registered with {} skills",
                    wrapper.getAgentCard().skills().size());
        } else {
            log.warn("Dynamic AgentCard is null, cannot register");
        }
    }
}