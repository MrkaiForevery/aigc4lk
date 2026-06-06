package com.air.commander.orchestrator;

import com.air.commander.intent.IntentClassifier;
import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.memory.MemoryContextBuilder;
import com.air.commander.memory.MemoryUpdatePipeline;
import com.air.commander.model.*;
import com.air.commander.quality.QualityAssessor;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 混合编排器
 * 核心编排逻辑句柄类
 */
@Service
@RequiredArgsConstructor
public class HybridOrchestrator {

    private final MemoryContextBuilder memoryContextBuilder;
    private final IntentClassifier intentClassifier;
    private final TemplateExecutor templateExecutor;
    private final DynamicOrchestrator dynamicOrchestrator;
    private final GraphExecutor graphExecutor;
    private final MemoryUpdatePipeline memoryUpdatePipeline;
    private final QualityAssessor qualityAssessor;
    private final InterruptHandler interruptHandler;

    @GlobalTransactional(timeoutMills = 1800000)
    public ExecutionPlan execute(ExecuteRequest request) {
        String userId = request.userId();
        String threadId = request.threadId();
        String userInput = request.input();
        Map<String, String> tokens = request.tokens();

        MemoryContext memoryCtx = memoryContextBuilder.build(userId, threadId, userInput);
        IntentResult intent = intentClassifier.classify(userInput, memoryCtx);

        OrchestrationPlan plan;
        if (intent.isTemplate()) {
            plan = templateExecutor.loadAndPersonalize(intent.templateId(), userInput, memoryCtx);
        } else {
            plan = dynamicOrchestrator.generatePlan(userInput, memoryCtx);
        }



        String xid = RootContext.getXID();
        List<ExecutionResult> results = graphExecutor.execute(plan, threadId, userId, tokens, xid, memoryCtx);

        CompletableFuture.runAsync(() -> {
            int score = qualityAssessor.evaluate(plan, results);
            memoryUpdatePipeline.update(threadId, userId, plan, results, score, memoryCtx);
        });

        return ExecutionPlan.builder()
                .mode(intent.isTemplate() ? "template" : "dynamic")
                .planId(plan.getPlanId())
                .results(results)
                .interrupted(results.stream().anyMatch(r -> r.getCommand() != null))
                .summary("Executed")
                .xid(xid)
                .build();
    }

    public record ExecuteRequest(String userId, String threadId, String input, Map<String, String> tokens) {

    }
}