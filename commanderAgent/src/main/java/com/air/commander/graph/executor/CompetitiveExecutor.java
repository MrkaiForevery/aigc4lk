package com.air.commander.graph.executor;

import com.air.commander.model.ExecutionResult;
import com.air.commander.model.MemoryContext;
import com.air.commander.model.OrchestrationPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CompetitiveExecutor {

    /**竞争模式执行**/
    public List<ExecutionResult> executeCompetitive(OrchestrationPlan plan,
                                                     String threadId,
                                                     String userId,
                                                     Map<String, String> tokens,
                                                     String xid,
                                                     MemoryContext memoryCtx, Map<String, Object> runtimeContext) {

        return null;
    }
}
