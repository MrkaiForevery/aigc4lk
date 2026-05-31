package com.air.memory.service;

import cn.hutool.json.JSONUtil;
import com.air.api.dto.KnowledgeResultDTO;
import com.air.api.dto.ProfileMemoryDTO;
import com.air.memory.entity.BehaviorRecord;
import com.air.memory.entity.KnowledgeResult;
import com.air.memory.entity.MemoryNode;
import com.air.memory.entity.ProfileMemory;
import com.air.memory.priority.MemoryPrioritySorter;
import com.air.memory.repository.unstructured.SessionMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryManagerService {

    // 记忆优先级排序器注入
    private final MemoryPrioritySorter prioritySorter;

    private final ProfileService profileService;
    private final BehaviorService behaviorService;
    private final KnowledgeService knowledgeService;
    private final SessionMemoryRepository sessionMemoryRepository;

    private final ChatModel chatModel;

    /**
     * 获取用户所有相关记忆（已排序）
     * 用于 Commander 构建增强上下文
     */
    public List<MemoryNode> getPrioritizedMemories(String userId, Map<String, ? extends Serializable> query, int limit) {
        List<MemoryNode> allMemories = new ArrayList<>();

        // 1. 加载画像记忆
        ProfileMemoryDTO profile = profileService.getProfile(userId);
        if (profile != null) {
            allMemories.add(MemoryNode.builder()
                    .memoryType("PROFILE")
                    .content(buildProfileContent(profile))
                    .timestamp(profile.getUpdatedAt())
                    .frequency(1)
                    .build());
        }

        // 2. 加载最近行为记忆
        List<BehaviorRecord> behaviors = behaviorService.getRecentBehavior(userId, 10);
        behaviors.forEach(b -> allMemories.add(MemoryNode.builder()
                .memoryType("BEHAVIOR")
                .content(b.getContent())
                .timestamp(b.getCreatedAt())
                .frequency(1)
                .build()));

        // 3. 加载相关长期知识
        List<KnowledgeResultDTO> knowledge = knowledgeService.search(query);
        knowledge.forEach(k -> allMemories.add(MemoryNode.builder()
                .memoryType("KNOWLEDGE")
                .content(k.getContent())
                .similarity(k.getSimilarity())
                .build()));

        // 4. 排序并截断
        return prioritySorter.sortAndLimit(allMemories, limit);
    }

    private String buildProfileContent(ProfileMemoryDTO profile) {
        return String.format("用户偏好模型: %s, 技术等级: %s, 兴趣领域: %s",
                profile.getPreferredModel(),
                profile.getTechnicalLevel(),
                profile.getTopicsOfInterest());
    }

    /**
     * 同步生成对话摘要
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 摘要内容
     */
    public String summarizeSession(String sessionId, String userId) {
        // 1. 从 Redis 获取会话消息
        List<String> messages = sessionMemoryRepository.getSessionMessages(sessionId);
        if (messages.isEmpty()) {
            return null;
        }

        // 2. 构建 LLM 提示词
        String fullText = String.join("\n", messages);
        String promptText = String.format("""
                请用100字以内总结以下对话的核心内容，提取关键信息和结论：
                
                对话内容：
                %s
                """, fullText);

        // 3. 调用 LLM 生成摘要
        Prompt prompt = new Prompt(new UserMessage(promptText));
        ChatResponse response = chatModel.call(prompt);
        String summary = response.getResult().getOutput().getText();

        // 4. 将摘要作为行为记录写入
        BehaviorRecord summaryRecord = BehaviorRecord.builder()
                .userId(userId)
                .sessionId(sessionId)
                .actionType("SESSION_SUMMARY")
                .content(summary)
                .metadata(JSONUtil.toJsonStr(Map.of(
                        "original_message_count", messages.size()
                )))
                .build();
        behaviorService.recordBehavior(summaryRecord);

        log.debug("对话摘要已保存: sessionId={}, summary={}", sessionId, summary);
        return summary;
    }

    /**
     * 异步生成对话摘要
     */
    @Async("ioExecutor")
    public CompletableFuture<String> summarizeSessionAsync(String sessionId, String userId) {
        return CompletableFuture.completedFuture(summarizeSession(sessionId, userId));
    }
}
