package com.air.memory.cleaner;

import com.air.memory.entity.CleanedMemory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 智能清洗器 —— 基于大模型的高质量清洗
 * 适用于：摘要提取、情感过滤、知识提取、画像标签提取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMBasedCleaner {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * LLM 清洗主入口（异步）
     */
    @Async("ioExecutor")
    public CompletableFuture<CleanedMemory> clean(CleanedMemory ruleCleaned) {
        if (!ruleCleaned.isValid()) {
            return CompletableFuture.completedFuture(ruleCleaned);
        }

        String promptText = buildCleanPrompt(ruleCleaned.getSummary(), ruleCleaned.getMemoryType());

        try {
            Prompt prompt = new Prompt(new UserMessage(promptText));
            ChatResponse chatResponse = chatModel.call(prompt);
            //todo 这里多模态要考虑一下不是getText
            String llmResponse = chatResponse.getResult().getOutput().getText();

            String jsonStr = extractJson(llmResponse);
            Map<String, Object> result = objectMapper.readValue(jsonStr, Map.class);

            return CompletableFuture.completedFuture(
                    CleanedMemory.builder()
                            .rawContent(ruleCleaned.getRawContent())
                            .valid(Boolean.TRUE.equals(result.get("valid")))
                            .summary(result.get("summary") != null 
                                    ? result.get("summary").toString() 
                                    : ruleCleaned.getSummary())
                            .knowledge(result.get("knowledge") != null 
                                    ? (Map<String, Object>) result.get("knowledge") 
                                    : null)
                            .profileTags(result.get("profile_tags") != null 
                                    ? (List<String>) result.get("profile_tags") 
                                    : null)
                            .memoryType(ruleCleaned.getMemoryType())
                            .confidence(0.95)
                            .cleanedAt(System.currentTimeMillis())
                            .cleanSource("LLM_BASED")
                            .remark(result.get("remark") != null 
                                    ? result.get("remark").toString() 
                                    : null)
                            .build()
            );
        } catch (JsonProcessingException e) {
            log.warn("LLM 清洗 JSON 解析失败，回退到规则清洗结果", e);
            ruleCleaned.setRemark("LLM 解析失败，使用规则清洗结果");
            return CompletableFuture.completedFuture(ruleCleaned);
        } catch (Exception e) {
            log.error("LLM 清洗异常", e);
            ruleCleaned.setValid(false);
            ruleCleaned.setRemark("LLM 清洗异常：" + e.getMessage());
            return CompletableFuture.completedFuture(ruleCleaned);
        }
    }

    /**
     * 构建清洗 Prompt
     */
    private String buildCleanPrompt(String content, String memoryType) {
        return String.format("""
                请清洗以下用户记忆（类型：%s）：
                
                1. 如果只是情绪宣泄（如抱怨、发泄、纯吐槽），标记为"无效"，不提取
                2. 如果有实质信息，提取核心要点作为摘要（不超过 100 字）
                3. 如果包含可复用的知识，提取为结构化 JSON（key-value 形式）
                4. 如果包含用户特征，提取画像标签（如：技术水平、兴趣领域、沟通偏好）
                5. 如果内容中包含时间、地点、人名等关键信息，在摘要中保留
                
                原始内容：
                %s
                
                请以 JSON 格式返回，不要包含其他内容：
                {
                    "valid": true/false,
                    "summary": "核心要点",
                    "knowledge": {"key": "value"} 或 null,
                    "profile_tags": ["标签1", "标签2"] 或 [],
                    "remark": "清洗说明（可选）"
                }
                """, memoryType, content);
    }

    /**
     * 从 LLM 响应中提取 JSON
     */
    private String extractJson(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) return "{}";
        int start = llmResponse.indexOf("{");
        int end = llmResponse.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return llmResponse.substring(start, end + 1);
        }
        return "{}";
    }
}