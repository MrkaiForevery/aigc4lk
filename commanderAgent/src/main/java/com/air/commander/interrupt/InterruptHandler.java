package com.air.commander.interrupt;

import com.air.commander.credential.CredentialService;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.InterruptContext;
import com.air.commander.model.OrchestrationPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.seata.core.exception.TransactionException;
import io.seata.tm.api.GlobalTransaction;
import io.seata.tm.api.GlobalTransactionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.core.context.RootContext;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 中断器核心处理类
 */
@Slf4j
@Component
public class InterruptHandler {

    private final RedissonClient redissonClient;
    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;

    public InterruptHandler(CredentialService credentialService,
                            RedissonClient redissonClient,
                            ObjectMapper objectMapper) {
        this.credentialService = credentialService;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }


    /**
     * 创建检查点并挂起全局事务
     */
    public void suspend(String xid,
                        String userId,
                        String threadId,
                        String stepId,
                        OrchestrationPlan plan,
                        int stepIndex,
                        Map<String, Object> runtimeContext,
                        ExecutionResult.Command command) {
        try {
            String currentXid = RootContext.getXID();
            if (currentXid != null) {
                RootContext.unbind();
            }
            // 显式标记中断步骤，用于恢复时识别自身
            runtimeContext.put(stepId + ".interrupted", true);
            runtimeContext.put(stepId + ".executionStatus", ExecutionResult.ExecutionStatus.SUSPEND);

            // 2. 构建检查点上下文
            InterruptContext ctx = InterruptContext.builder()
                    .xid(xid)
                    .userId(userId)
                    .threadId(threadId)
                    .planId(plan.getPlanId())
                    .currentStepId(stepId)
                    .stepIndex(stepIndex)
                    // 保存整个序列后的计划，用于崩溃恢复
                    .planJson(objectMapper.writeValueAsString(plan))
                    .commandType(command.getType())
                    .question(command.getMessage())
                    .requiredScopes(command.getRequiredScopes())
                    .runtimeContext(runtimeContext)
                    .mode(plan.getMode())
                    .createdAt(Instant.now())
                    .timeoutAt(Instant.now().plus(Duration.ofMinutes(60)))
                    .build();

            // 3. 存入 Redis，60 分钟过期
            redissonClient.getBucket("interrupt:" + xid).set(ctx, Duration.ofMinutes(60));
            log.info("检查点已创建: xid={}, stepId={}, type={}", xid, stepId, command.getType());

        } catch (JsonProcessingException e) {
            log.error("【建立suspend】序列化执行计划失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 从检查点恢复事务并返回用户凭证
     */
    public Map<String, String> resume(String xid, List<String> approvedScopes) {
        // 1. 从 Redis 加载检查点
        RBucket<InterruptContext> bucket = redissonClient.getBucket("interrupt:" + xid);
        InterruptContext ctx = bucket.get();
        if (ctx == null) {
            throw new RuntimeException("检查点不存在或已过期: " + xid);
        }

        // 2. 获取用户授权的 Token
        Map<String, String> tokens = credentialService.approve(ctx.getUserId(), approvedScopes);
        //todo 这里增加token校验逻辑，如果解析token失败，则直接返回没有权限

        // 3. 恢复全局事务：重新绑定 XID 到当前线程
        RootContext.bind(xid);

        return tokens;
    }

    /**
     * 回滚全局事务并清理检查点
     */
    public void rollback(String xid) {
        RBucket<InterruptContext> bucket = redissonClient.getBucket("interrupt:" + xid);
        InterruptContext ctx = bucket.get();

        try {
            // 绑定 XID 以便回滚
            RootContext.bind(xid);
            GlobalTransaction tx = GlobalTransactionContext.reload(xid);
            tx.rollback();
        } catch (TransactionException e) {
            log.error("回滚全局事务失败: xid={}", xid, e);
            throw new RuntimeException("事务回滚失败", e);
        } finally {
            bucket.delete();
        }
    }

    /**
     * 获取某个检查点状态
     */
    public InterruptContext getStatus(String xid) {
        return (InterruptContext) redissonClient.getBucket("interrupt:" + xid).get();
    }
}