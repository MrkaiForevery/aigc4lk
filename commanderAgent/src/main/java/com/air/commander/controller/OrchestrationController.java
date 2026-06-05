package com.air.commander.controller;

import com.air.commander.interrupt.InterruptHandler;
import com.air.commander.model.ExecutionPlan;
import com.air.commander.orchestrator.HybridOrchestrator;
import io.seata.core.exception.TransactionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrchestrationController {

    private final HybridOrchestrator orchestrator;
    private final InterruptHandler interruptHandler;

    @PostMapping("/execute")
    public ResponseEntity<ExecutionPlan> execute(@RequestBody HybridOrchestrator.ExecuteRequest req) {
        return ResponseEntity.ok(orchestrator.execute(req));
    }

    @PostMapping("/interrupt/{xid}/respond")
    public ResponseEntity<?> respond(@PathVariable String xid, @RequestBody Map<String, Object> body) throws TransactionException {
        boolean approved = (boolean) body.get("approved");
        List<String> scopes = (List<String>) body.get("scopes");
        if (approved) {
            Map<String, String> tokens = interruptHandler.resume(xid, scopes);
            return ResponseEntity.ok(Map.of("status", "resumed", "tokens", tokens));
        } else {
            interruptHandler.rollback(xid);
            return ResponseEntity.ok(Map.of("status", "rolled_back"));
        }
    }
}