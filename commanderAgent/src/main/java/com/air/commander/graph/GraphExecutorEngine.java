package com.air.commander.graph;

import com.air.commander.graph.executor.*;
import com.air.commander.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 流程图的具体实施执行器
 */
@Slf4j
@Component
public class GraphExecutorEngine {

    private final SequentialExecutor sequentialExecutor;
    private final ParallelExecutor parallelExecutor;
    private final ConditionalExecutor conditionalExecutor;
    private final IterativeCorrectionExecutor iterativeCorrectionExecutor;
    private final CompetitiveExecutor competitiveExecutor;

    public GraphExecutorEngine(SequentialExecutor sequentialExecutor,
                               ParallelExecutor parallelExecutor,
                               ConditionalExecutor conditionalExecutor,
                               IterativeCorrectionExecutor iterativeCorrectionExecutor,
                               CompetitiveExecutor competitiveExecutor) {
        this.sequentialExecutor = sequentialExecutor;
        this.parallelExecutor = parallelExecutor;
        this.conditionalExecutor = conditionalExecutor;
        this.iterativeCorrectionExecutor = iterativeCorrectionExecutor;
        this.competitiveExecutor = competitiveExecutor;
    }


    public List<ExecutionResult> execute(OrchestrationPlan plan,
                                         String threadId,
                                         String userId,
                                         Map<String, String> tokens,
                                         String xid,
                                         MemoryContext memoryCtx) {
        return execute(plan, threadId, userId, tokens, xid, memoryCtx, new ConcurrentHashMap<>());
    }

    public List<ExecutionResult> execute(OrchestrationPlan plan,
                                         String threadId,
                                         String userId,
                                         Map<String, String> tokens,
                                         String xid,
                                         MemoryContext memoryCtx,
                                         Map<String, Object> runtimeContext) {
        return switch (plan.getExecutionMode()) {
            case SEQUENTIAL -> sequentialExecutor.executeSequential(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case PARALLEL -> parallelExecutor.executeParallel(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case CONDITIONAL -> conditionalExecutor.executeConditional(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case ITERATIVE_CORRECTION -> iterativeCorrectionExecutor.executeIterativeCorrection(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
            case COMPETITIVE -> competitiveExecutor.executeCompetitive(plan, threadId, userId, tokens, xid, memoryCtx,runtimeContext);
        };
    }
}