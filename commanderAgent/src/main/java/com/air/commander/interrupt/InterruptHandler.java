package com.air.commander.interrupt;

import com.air.commander.credential.CredentialService;
import com.air.commander.model.ExecutionResult;
import com.air.commander.model.InterruptContext;
import io.seata.core.exception.TransactionException;
import io.seata.tm.api.GlobalTransaction;
import io.seata.tm.api.GlobalTransactionContext;
import io.seata.tm.api.transaction.SuspendedResourcesHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 中断器核心处理类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterruptHandler {

    private final RedissonClient redissonClient;
    private final CredentialService credentialService;

    /**
     * 挂起全局事务并保存中断上下文到 Redis
     *
     * @param xid     Seata 全局事务 ID
     * @param userId  用户 ID
     * @param stepId  中断步骤 ID
     * @param command 中断命令（包含类型、权限范围等）
     */
    public void suspend(String xid, String userId, String stepId, ExecutionResult.Command command) {
        GlobalTransaction tx = GlobalTransactionContext.getCurrent();
        SuspendedResourcesHolder holder = null;
        try {
            // 挂起事务并获取快照
            holder = tx.suspend();
        } catch (TransactionException e) {
            log.error("挂起全局事务失败, xid={}", xid, e);
            throw new RuntimeException("事务挂起失败", e);
        }

        InterruptContext ctx = InterruptContext.builder()
                .xid(xid)
                .userId(userId)
                .stepId(stepId)
                .commandType(command.getType())
                .requiredScopes(command.getRequiredScopes())
                .transactionHolder(holder)
                .build();

        RBucket<InterruptContext> bucket = redissonClient.getBucket("interrupt:" + xid);
        bucket.set(ctx, Duration.ofMinutes(30));
    }

    /**
     * 用户批准后恢复事务
     *
     * @param xid    事务 ID
     * @param scopes 用户批准的权限范围列表
     * @return 凭证 Token Map
     * @throws TransactionException 事务异常
     */
    public Map<String, String> resume(String xid, List<String> scopes) throws TransactionException {
        RBucket<InterruptContext> bucket = redissonClient.getBucket("interrupt:" + xid);
        InterruptContext ctx = bucket.get();
        if (ctx == null || ctx.getTransactionHolder() == null) {
            throw new RuntimeException("找不到事务挂起信息，xid=" + xid);
        }

        // 获取凭证
        Map<String, String> tokens = credentialService.approve(ctx.getUserId(), scopes);

        // 恢复事务
        GlobalTransaction tx = GlobalTransactionContext.reload(xid);
        try {
            tx.resume(ctx.getTransactionHolder());
        } catch (TransactionException e) {
            log.error("恢复全局事务失败, xid={}", xid, e);
            throw e;
        }

        // 清理
        bucket.delete();
        return tokens;
    }

    /**
     * 用户拒绝后回滚事务
     */
    public void rollback(String xid) throws TransactionException {
        RBucket<InterruptContext> bucket = redissonClient.getBucket("interrupt:" + xid);
        InterruptContext ctx = bucket.get();

        GlobalTransaction tx = GlobalTransactionContext.reload(xid);
        try {
            if (ctx != null && ctx.getTransactionHolder() != null) {
                tx.resume(ctx.getTransactionHolder());
            } else {
                log.warn("未找到事务快照，尝试直接回滚, xid={}", xid);
            }
            tx.rollback();
        } catch (TransactionException e) {
            log.error("回滚全局事务失败, xid={}", xid, e);
            throw e;
        } finally {
            bucket.delete();
        }
    }
}