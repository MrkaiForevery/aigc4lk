package com.air.document.skills;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SkillDocumentLoader {

    /**
     * 根据 skillId 加载 classpath 下的 skills/{skillId}.md 文件内容
     * @param skillId 技能ID，如 "document_generation"
     * @return Markdown 内容，文件不存在或读取失败返回空字符串
     */
    public String loadSkillDocument(String skillId) {
        String path = "skills/skill_" + skillId + ".md";
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return "";
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}