package com.air.memory.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.air.api.dto.conversation.ConversationHistoryDTO;
import com.air.api.dto.conversation.ExecutionResultDTO;
import com.air.api.dto.conversation.OrchestrationPlanDTO;
import com.air.memory.model.ConversationHistory;
import com.air.memory.model.ExecutionResultHistory;
import com.air.memory.model.PlanHistory;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationHistoryManagerService {

    private final StructuredMemoryRepository structuredMemoryRepository;

    @Transactional
    public void doSaveOneConversationHistory(ConversationHistoryDTO conversationHistoryDTO) {
        String userId = conversationHistoryDTO.getUserId();
        String threadId = conversationHistoryDTO.getThreadId();

        //判读该会话是否存在
        boolean existConversation = structuredMemoryRepository.existConversationByUserIdAndThreadId(userId, threadId);
        //如果不存在则执行插入，存在则只需要该变status状态
        if (!existConversation) {
            //转化ConversationHistory对象
            ConversationHistory conversationHistory = this.transferConversationHistory(conversationHistoryDTO);
            //插入新增的会话历史
            structuredMemoryRepository.saveOneConversationHistory(conversationHistory);
        }

        //转化planHistory对象
        PlanHistory planHistory = transferPlanHistory(conversationHistoryDTO);
        //判断planId是否存在
        Integer existPlanHistoryId = structuredMemoryRepository.existPlanHistory(threadId, planHistory.getPlanId());
        if (existPlanHistoryId > 0) {
            planHistory.setId(existPlanHistoryId);
        }
        //planHistory的主键存在就修改，不存在就插入
        structuredMemoryRepository.updatePlanHistory(planHistory);

        //转化executeStepHistory
        List<ExecutionResultHistory> updateResults = transferExecuteStepsHistory(conversationHistoryDTO);
        //判断planId下的ExecutionResult是否存在
        List<ExecutionResultHistory> existedResultHistories = structuredMemoryRepository.existedExecutionResultHistoryByPlanId(planHistory.getPlanId());
        Map<String, ExecutionResultHistory> existedResultHistoriesMap = existedResultHistories.stream()
                .collect(Collectors.toMap(
                        ExecutionResultHistory::getStepId,
                        Function.identity(),
                        (old, newVal) -> newVal
                ));
        updateResults.forEach(r -> {
            ExecutionResultHistory history = existedResultHistoriesMap.get(r.getStepId());
            if (history != null) {
                r.setId(history.getId());
            }
        });
        //执行批量插入或批量更新
        structuredMemoryRepository.batchUpdateExecutionResultHistory(updateResults);
    }


    private ConversationHistory transferConversationHistory(ConversationHistoryDTO dto) {
        return ConversationHistory.builder()
                .createdBy("admin111")
                .userId(dto.getUserId())
                .threadId(dto.getThreadId())
                .status("IN_PROGRESS")
                .build();
    }


    private PlanHistory transferPlanHistory(ConversationHistoryDTO dto) {
        OrchestrationPlanDTO planDTO = dto.getPlanDTO();
        return PlanHistory.builder()
                .threadId(dto.getThreadId())
                .relationRequestId(planDTO.getRelationRequestId())
                .relationTemplateId(planDTO.getRelationTemplateId())
                .userInput(dto.getUserInput())
                .planId(planDTO.getPlanId())
                .planContentJsonb(JSONUtil.toJsonStr(planDTO))
                .executeStepId(this.findExecutingStepId(dto))
                .createdBy("admin111")
                .build();
    }

    private String findExecutingStepId(ConversationHistoryDTO dto) {
        List<ExecutionResultDTO> executionResultDTOS = dto.getExecutionResultDTOS();
        ExecutionResultDTO lastResult = executionResultDTOS.get(executionResultDTOS.size() - 1);
        return lastResult.getStepId();
    }

    private List<ExecutionResultHistory> transferExecuteStepsHistory(ConversationHistoryDTO conversationHistoryDTO) {
        List<ExecutionResultDTO> executionResultDTOS = conversationHistoryDTO.getExecutionResultDTOS();
        List<ExecutionResultHistory> resultHistories = executionResultDTOS.stream()
                .map(dto -> {
                    String executeStatus = dto.isSuccess() ? "success" : "error";
                    return ExecutionResultHistory.builder()
                            .relationPlanId(dto.getRelationPlanId())
                            .stepId(dto.getStepId())
                            .resultContentJsonb(JSONUtil.toJsonStr(dto))
                            .executeStatus(executeStatus)
                            .executeTime(dto.getDurationMs())
                            .createdBy("admin111")
                            .build();
                })
                .collect(Collectors.toList());
        return resultHistories;
    }

}
