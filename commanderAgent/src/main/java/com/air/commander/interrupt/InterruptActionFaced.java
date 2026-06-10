package com.air.commander.interrupt;

import com.air.commander.model.ExecutionPlan;
import com.air.commander.orchestrator.HybridOrchestratorManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        //用户同意授权执行则恢复继续往下执行，不同意授权则回滚整个事务
        if (approved) {
            // 1. 恢复事务并获取凭证
            Map<String, String> tokens = interruptHandler.resume(xid, scopes);
            //todo 这里以后还要插入校验token返回的结果
            // 2. 继续执行后续步骤
            ExecutionPlan remainingPlan = hybridOrchestratorManager.resumeExecution(xid, tokens);
            return Map.of("status", "resumed", "plan", remainingPlan);
        }else {
            interruptHandler.rollback(xid);
            return Map.of("status", "rolled_back");
        }
    }

}
