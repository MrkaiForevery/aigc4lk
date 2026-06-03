package com.air.document.tools;

import com.air.api.dto.IdentityMemoryDTO;
import com.air.api.dto.KnowledgeResultDTO;
import com.air.api.dto.PreferenceMemoryDTO;
import com.air.api.dto.ProfileMemoryDTO;
import com.air.api.feignClient.MemoryIdentityFeign;
import com.air.api.feignClient.MemoryKnowledgeFeign;
import com.air.api.feignClient.MemoryPreferenceFeign;
import com.air.api.feignClient.MemoryProfileFeign;
import com.air.platform.common.a2a.enums.A2AMessageType;
import com.air.platform.common.a2a.protocol.A2AMessage;
import com.air.platform.common.a2a.protocol.A2AResponse;
import com.air.platform.common.a2a.router.NacosA2ARouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentMemoryTools {

    private final MemoryIdentityFeign memoryIdentityFeign;
    private final MemoryProfileFeign memoryProfileFeign;
    private final MemoryKnowledgeFeign memoryKnowledgeFeign;
    private final MemoryPreferenceFeign memoryPreferenceFeign;

    /**注入平台自定义的a2aRouter**/
    private final NacosA2ARouter a2aRouter;

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


    //----------------------------调用AnalysisAgent服务的tools集合--------------------------------//
    @Tool(description = "调用 analysis-agent 进行数据或文本分析，返回分析结果")
    public String callAnalysisAgent(
            @ToolParam(description = "需要分析的内容或数据") String content,
            @ToolParam(description = "分析类型，如 sentiment/trend/summary") String analysisType) {
        try {
            A2AMessage message = A2AMessage.builder()
                    .senderAgentId("document-agent")
                    .receiverAgentId("analysis-agent")     // 目标子 Agent 的服务名
                    .messageType(A2AMessageType.TASK_DELEGATION)
                    .payload(Map.of(
                            "taskId", UUID.randomUUID().toString(),
                            "taskType", analysisType,
                            "payload", Map.of("content", content)
                    ))
                    .build();
            // 2. 构建发送参数
            A2AResponse a2AResponse = a2aRouter.routeMessage(message);

            if (A2AResponse.ResponseStatus.SUCCESS.equals(a2AResponse.getStatus())) {
                return a2AResponse.getPayload().toString();
            } else {
                return "分析失败: " + a2AResponse.getErrorMessage();
            }
        } catch (Exception e) {
            return "调用分析 Agent 出错: " + e.getMessage();
        }
    }


}