package com.air.document.skills;

import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.AgentProvider;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DynamicAgentCardProvider implements AgentCardProvider {

    private final AgentSkillsProperties agentSkillsProperties;
    private final SkillDocumentLoader documentLoader;

    public DynamicAgentCardProvider(AgentSkillsProperties agentSkillsProperties,
                                    SkillDocumentLoader documentLoader) {
        this.agentSkillsProperties = agentSkillsProperties;
        this.documentLoader = documentLoader;
    }

    @Override
    public AgentCardWrapper getAgentCard() {
        // 1. 构建技能列表（使用 A2A 标准包中的 AgentSkill）
        List<AgentSkill> skills = agentSkillsProperties.getSkills().stream()
                .map(sc -> {
                    String fullDescription = sc.getDescription();
                    String docContent = documentLoader.loadSkillDocument(sc.getSkillId());
                    if (!docContent.isEmpty()) {
                        fullDescription += "\n\n" + docContent;
                    }

                    // 使用 io.a2a.spec.AgentSkill 的 Builder
                    return new AgentSkill.Builder()
                            .id(sc.getSkillId())
                            .name(sc.getSkillId())
                            .description(fullDescription)
                            .tags(sc.getTags())
                            .build();
                })
                .toList();

        // 2. 构建 A2A 标准协议的 AgentCard
        AgentCard agentCard =new AgentCard.Builder()
                .capabilities(new AgentCapabilities.Builder().build())
                .defaultInputModes(List.of("text", "text/plain"))
                .defaultOutputModes(List.of("text", "text/plain"))
                .description(agentSkillsProperties.getDescription())
                .name(agentSkillsProperties.getName())
                .preferredTransport("jsonrpc")
                .skills(skills)
                .url("https://www.air.com")
                .provider(new AgentProvider("air.com","https://www.air.com"))
                .version("0.0.1")
                .protocolVersion("1.0")
                .build();

        // 3. 包装并返回
        AgentCardWrapper wrapper = new AgentCardWrapper(agentCard);
        log.info("Dynamic AgentCard built with {} skills (docs from classpath)", skills.size());
        return wrapper;
    }
}