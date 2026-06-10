package com.air.commander.interrupt;

import com.air.commander.model.ExecutionPlan;
import com.air.commander.orchestrator.HybridOrchestratorManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * checkPoint恢复的统一入口(提供给外部调用)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterruptActionFaced {

    private final InterruptHandler interruptHandler;
    private final HybridOrchestratorManager hybridOrchestratorManager;


    public Map<String,Object> doRespond(String xid,Map<String, Object> body){
        boolean approved = (boolean) body.get("approved");
        List<String> scopes = (List<String>) body.get("scopes");
        if (approved) {
            // 1. 恢复事务并获取凭证
            Map<String, String> tokens = interruptHandler.resume(xid, scopes);

            // 2. 继续执行后续步骤
            ExecutionPlan remainingPlan = hybridOrchestratorManager.resumeExecution(xid, tokens);
            return Map.of("status", "resumed", "plan", remainingPlan);
        } else {
            interruptHandler.rollback(xid);
            return Map.of("status", "rolled_back");
        }
    }

}
