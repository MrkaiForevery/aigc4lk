package com.air.memory.entity.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ProfileMemory.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistoryEntity {

    private String threadId;
    private String userId;
    private String userInput;
    private OrchestrationPlanEntity plan;
    private List<ExecutionResultEntity> results;
    private MemoryContextEntity memoryContext;

}