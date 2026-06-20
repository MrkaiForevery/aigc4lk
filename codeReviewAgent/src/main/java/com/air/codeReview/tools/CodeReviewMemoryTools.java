package com.air.codeReview.tools;

import com.air.api.dto.IdentityMemoryDTO;
import com.air.api.dto.KnowledgeResultDTO;
import com.air.api.dto.PreferenceMemoryDTO;
import com.air.api.dto.ProfileMemoryDTO;
import com.air.api.feignClient.MemoryIdentityFeign;
import com.air.api.feignClient.MemoryKnowledgeFeign;
import com.air.api.feignClient.MemoryPreferenceFeign;
import com.air.api.feignClient.MemoryProfileFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewMemoryTools {

    private final MemoryIdentityFeign memoryIdentityFeign;
    private final MemoryProfileFeign memoryProfileFeign;
    private final MemoryKnowledgeFeign memoryKnowledgeFeign;
    private final MemoryPreferenceFeign memoryPreferenceFeign;


    //----------------------------调用Memory服务的tools集合--------------------------------//
    @Tool(description = "获取用户身份信息")
    public IdentityMemoryDTO getIdentity(@ToolParam(description = "用户ID") String userId) {
        return memoryIdentityFeign.get(userId);
    }

    @Tool(description = "获取用户画像，包含技术等级、兴趣领域、沟通风格等")
    public ProfileMemoryDTO getProfile(@ToolParam(description = "用户ID") String userId) {
        return memoryProfileFeign.getProfile(userId);
    }

    @Tool(description = "获取用户偏好设置，包含输出风格、是否显示中间步骤等")
    public PreferenceMemoryDTO getPreference(@ToolParam(description = "用户ID") String userId) {
        return memoryPreferenceFeign.getPreference(userId);
    }

    @Tool(description = "从知识库中搜索与主题相关的内容，返回相关文档列表")
    public List<KnowledgeResultDTO> searchKnowledge(@ToolParam(description = "搜索主题") String topic) {
        return memoryKnowledgeFeign.searchKnowledge(Map.of("queryString", topic, "limit", 5));
    }

    //----------------------------调用其他同级别agent服务的tools集合--------------------------------//



}