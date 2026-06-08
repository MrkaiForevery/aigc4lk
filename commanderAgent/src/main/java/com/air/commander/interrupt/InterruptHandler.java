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
import io.seata.tm.api.transaction.SuspendedResourcesHolder;
import lombok.extern.slf4j.Slf4j;
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
            // 1. 冻结分布式事务
            GlobalTransaction tx = GlobalTransactionContext.getCurrent();
            if (tx == null) {
                log.error("无法挂起事务：当前无活动全局事务，xid={}", xid);
                // 清理可能残留的中断上下文
                redissonClient.getBucket("interrupt:" + xid).delete();
                return; // 直接返回，不抛异常
            }

            SuspendedResourcesHolder holder = tx.suspend();

            // 2. 构建检查点上下文
            InterruptContext ctx = InterruptContext.builder()
                    .xid(xid)
                    .userId(userId)
                    .threadId(threadId)
                    .planId(plan.getPlanId())
                    .currentStepId(stepId)
                    .stepIndex(stepIndex)
                    // 保存整个计划，用于崩溃恢复
                    .planJson(objectMapper.writeValueAsString(plan))
                    .commandType(command.getType())
                    .question(command.getMessage())
                    .requiredScopes(command.getRequiredScopes())
                    .runtimeContext(runtimeContext)
                    .transactionHolder(holder)
                    .createdAt(Instant.now())
                    .timeoutAt(Instant.now().plus(Duration.ofMinutes(30)))
                    .build();

            // 3. 存入 Redis，30 分钟过期
            RBucket<InterruptContext> bucket = redissonClient.getBucket("interrupt:" + xid);
            bucket.set(ctx, Duration.ofMinutes(30));

            log.info("检查点已创建: xid={}, stepId={}, type={}", xid, stepId, command.getType());

        } catch (TransactionException e) {
            log.error("挂起全局事务失败: xid={}", xid, e);
            throw new RuntimeException("事务挂起失败", e);
        } catch (JsonProcessingException e) {
            log.error("序列化执行计划失败", e);
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
        if (ctx == null || ctx.getTransactionHolder() == null) {
            throw new RuntimeException("检查点不存在或已过期: " + xid);
        }

        // 2. 获取用户授权的 Token
        Map<String, String> tokens = credentialService.approve(ctx.getUserId(), approvedScopes);

        // 3. 恢复全局事务
        try {
            GlobalTransaction tx = GlobalTransactionContext.reload(xid);
            tx.resume(ctx.getTransactionHolder());
        } catch (TransactionException e) {
            log.error("恢复全局事务失败: xid={}", xid, e);
            throw new RuntimeException("事务恢复失败", e);
        }

        // 4. 清理检查点
        bucket.delete();
        log.info("检查点已恢复并清除: xid={}", xid);

        return tokens;
    }

    /**
     * 回滚全局事务并清理检查点
     */
    public void rollback(String xid) {
        RBucket<InterruptContext> bucket = redissonClient.getBucket("interrupt:" + xid);
        InterruptContext ctx = bucket.get();

        try {
            GlobalTransaction tx = GlobalTransactionContext.reload(xid);
            if (ctx != null && ctx.getTransactionHolder() != null) {
                tx.resume(ctx.getTransactionHolder());  // 必须先恢复才能回滚
            }
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