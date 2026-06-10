package com.air.commander.controller;

import com.air.commander.interrupt.InterruptActionFaced;
import com.air.commander.model.ExecutionPlan;
import com.air.commander.orchestrator.HybridOrchestratorManager;
import io.seata.core.exception.TransactionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrchestrationController {

    private final HybridOrchestratorManager hybridOrchestratorManager;
    private final InterruptActionFaced interruptActionFaced;

    @PostMapping("/execute")
    public ResponseEntity<ExecutionPlan> execute(@RequestBody HybridOrchestratorManager.ExecuteRequest req) {
        ExecutionPlan executeResult = hybridOrchestratorManager.execute(req);
        return ResponseEntity.ok(executeResult);
    }

    @PostMapping("/interrupt/{xid}/respond")
    public ResponseEntity<?> respond(@PathVariable String xid, @RequestBody Map<String, Object> body) throws TransactionException {
        Map<String, Object> respondMapResult = interruptActionFaced.doRespond(xid, body);
        return ResponseEntity.ok(respondMapResult);
    }
}