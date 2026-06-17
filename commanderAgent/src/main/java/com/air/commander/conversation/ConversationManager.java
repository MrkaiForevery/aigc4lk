package com.air.commander.conversation;

import cn.hutool.core.bean.BeanUtil;
import com.air.api.dto.conversation.ConversationHistoryDTO;
import com.air.api.dto.conversation.ExecutionResultDTO;
import com.air.api.dto.conversation.MemoryContextDTO;
import com.air.api.dto.conversation.OrchestrationPlanDTO;
import com.air.api.feignClient.MemoryConversationHistoryFeign;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会话管理器
 */
@Component
@RequiredArgsConstructor
public class ConversationManager {

    private final RedissonClient redissonClient;
    private final MemoryConversationHistoryFeign memoryConversationHistoryFeign;


    /**
     * 获取指定 threadId 的最近 n 条消息
     */
    public List<MemoryContext.Message> getRecentMessages(String threadId, int n) {
        String key = "conv:" + threadId;
        RList<MemoryContext.Message> list = redissonClient.getList(key);
        // range 直接返回指定范围的 List<Message>，索引负数表示从尾部计算
        return list.range(-n, -1);
    }

    /**
     * 用户会话历史记录
     * @param threadId 会话id
     * @param userId 用户id
     * @param userInput 用户提问
     * @param plan 编排计划实体
     * @param results 步骤执行结果集
     * @param oldCtx 使用的记忆上下文
     */
    public void saveConversationHistory(String threadId,
                                        String userId,
                                        String userInput,
                                        OrchestrationPlan plan,
                                        List<ExecutionResult> results,
                                        MemoryContext oldCtx) {

        //构建存储对象
         ConversationHistoryDTO requestDto = ConversationHistoryDTO.builder()
                .threadId(threadId)
                .userId(userId)
                .userInput(userInput)
                .build();

         //构建planDto对象
        OrchestrationPlanDTO planDTO = BeanUtil.copyProperties(plan, OrchestrationPlanDTO.class);
        requestDto.setPlanDTO(planDTO);

        //构建ExecutionResult列表
        List<ExecutionResultDTO> executionResultDTOS = BeanUtil.copyToList(results, ExecutionResultDTO.class);
        requestDto.setExecutionResultDTOS(executionResultDTOS);

        //构建oldMemoryContext对象
        MemoryContextDTO memoryContextDTO = BeanUtil.copyProperties(oldCtx, MemoryContextDTO.class);
        requestDto.setMemoryContextDTO(memoryContextDTO);

        memoryConversationHistoryFeign.saveOne(requestDto);
    }
}