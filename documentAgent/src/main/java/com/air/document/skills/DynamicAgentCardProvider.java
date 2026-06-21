package com.air.document.skills;

import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.AgentProvider;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicAgentCardProvider implements AgentCardProvider {

    private final AgentSkillsProperties agentSkillsProperties;
    private final SkillDocumentLoader documentLoader;

    @PostConstruct
    public void init() {
        log.info("DynamicAgentCardProvider 被初始化了！");
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
                            .name(sc.getSkillId())
                            .description(fullDescription)
                            .build();
                })
                .toList();

        // 2. 构建 A2A 标准协议的 AgentCard
        AgentCard agentCard =new AgentCard.Builder()
                .name(agentSkillsProperties.getName())
                .description(agentSkillsProperties.getDescription())
                .skills(skills)
                .provider(new AgentProvider("air.com","https://www.air.com"))
                .build();

        // 3. 包装并返回
        AgentCardWrapper wrapper = new AgentCardWrapper(agentCard);
        log.info("Dynamic AgentCard built with {} skills (docs from classpath)", skills.size());
        return wrapper;
    }
}