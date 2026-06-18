package com.air.memory.service;

import cn.hutool.core.util.ObjectUtil;
import com.air.api.dto.conversation.ConversationHistoryDTO;
import com.air.api.dto.conversation.ExecutionResultDTO;
import com.air.api.dto.conversation.OrchestrationPlanDTO;
import com.air.memory.model.ConversationHistory;
import com.air.memory.model.ExecutionResultHistory;
import com.air.memory.model.PlanHistory;
import com.air.memory.repository.structured.StructuredMemoryRepository;
import com.air.memory.utils.MKJsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
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
    public void doSaveOneConversationHistory(ConversationHistoryDTO conversationHistoryDTO) throws SQLException {
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
        if (!ObjectUtil.isEmpty(existPlanHistoryId)) {
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
                .createdAt(LocalDateTime.now())
                .build();
    }


    private PlanHistory transferPlanHistory(ConversationHistoryDTO dto) throws SQLException {
        OrchestrationPlanDTO planDTO = dto.getPlanDTO();
        return PlanHistory.builder()
                .threadId(dto.getThreadId())
                .relationRequestId(planDTO.getRelationRequestId())
                .relationTemplateId(planDTO.getRelationTemplateId())
                .userInput(dto.getUserInput())
                .planId(planDTO.getPlanId())
                .planContentJsonb(MKJsonUtils.transferToPGJsonB(planDTO))
                .executeStepId(this.findExecutingStepId(dto))
                .createdBy("admin111")
                .createdTime(LocalDateTime.now())
                .build();
    }

    private String findExecutingStepId(ConversationHistoryDTO dto) {
        List<ExecutionResultDTO> executionResultDTOS = dto.getExecutionResultDTOS();
        ExecutionResultDTO lastResult = executionResultDTOS.get(executionResultDTOS.size() - 1);
        return lastResult.getStepId();
    }

    private List<ExecutionResultHistory> transferExecuteStepsHistory(ConversationHistoryDTO conversationHistoryDTO) {
        List<ExecutionResultDTO> executionResultDTOS = conversationHistoryDTO.getExecutionResultDTOS();
        String planId = conversationHistoryDTO.getPlanDTO().getPlanId();
        List<ExecutionResultHistory> resultHistories = executionResultDTOS.stream()
                .map(dto -> {
                    String executeStatus = dto.isSuccess() ? "success" : "error";
                    try {
                        return ExecutionResultHistory.builder()
                                .relationPlanId(planId)
                                .stepId(dto.getStepId())
                                .resultContentJsonb(MKJsonUtils.transferToPGJsonB(dto))
                                .executeStatus(dto.getExecutionStatus().toString())
                                .executeTime(dto.getDurationMs())
                                .createdBy("admin111")
                                .createdTime(LocalDateTime.now())
                                .build();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
        return resultHistories;
    }




}
