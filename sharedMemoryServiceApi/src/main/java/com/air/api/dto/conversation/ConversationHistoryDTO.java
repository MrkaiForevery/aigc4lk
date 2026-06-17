package com.air.api.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

// ProfileMemory.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistoryDTO implements Serializable {

    private String threadId;
    private String userId;
    private String userInput;
    private OrchestrationPlanDTO planDTO;
    private List<ExecutionResultDTO> executionResultDTOS;
    private MemoryContextDTO memoryContextDTO;
    private String createdBy;
    private Date createdAt;

}