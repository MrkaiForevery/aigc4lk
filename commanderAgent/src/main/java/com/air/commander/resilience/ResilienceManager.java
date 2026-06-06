package com.air.commander.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 多层级限流管理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilienceManager {

    private final CircuitBreakerRegistry cbRegistry;
    private final RateLimiterRegistry rlRegistry;
    private final BulkheadRegistry bhRegistry;
    private final TimeLimiterRegistry tlRegistry;

    // TimeLimiter 内部超时调度使用自己的 ScheduledExecutorService，
    // 这里只需要一个执行业务逻辑的线程池（用于 CompletableFuture.supplyAsync）
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(30);

    /**
     * 全防护装饰顺序：
     * Fallback( TimeLimiter( CircuitBreaker( Bulkhead( RateLimiter( Supplier ) ) ) ) )
     */
    public <T> T executeWithFullProtection(String name,
                                           Supplier<T> primary,
                                           Supplier<T> fallback) {
        RateLimiter rl = rlRegistry.rateLimiter(name);
        Bulkhead bh = bhRegistry.bulkhead(name);
        CircuitBreaker cb = cbRegistry.circuitBreaker(name);
        TimeLimiter tl = tlRegistry.timeLimiter(name);

        // 1. 先应用 RateLimiter、Bulkhead、CircuitBreaker（同步）
        Supplier<T> protectedSupplier = Decorators.ofSupplier(primary)
                .withRateLimiter(rl)
                .withBulkhead(bh)
                .withCircuitBreaker(cb)
                .decorate();

        // 2. 用 TimeLimiter 包装：通过 CompletableFuture 施加超时，并同步等待结果
        Supplier<T> timedSupplier = () -> {
            try {
                return tl.executeFutureSupplier(
                        () -> CompletableFuture.supplyAsync(protectedSupplier, asyncExecutor)
                );
            } catch (Exception e) {
                // TimeLimiter 超时或其他异常会被包裹为 RuntimeException 抛出，
                // 这样后面的 fallback 才能正常捕获
                throw new RuntimeException(e);
            }
        };

        // 3. 最后加上 Fallback
        Supplier<T> decorated = Decorators.ofSupplier(timedSupplier)
                .withFallback(throwable -> {
                    log.error("降级触发，异常类型: {}", throwable.getClass().getName());
                    return fallback.get();
                })
                .decorate();

        return decorated.get();
    }

    /**
     * 仅 CircuitBreaker + TimeLimiter + Fallback:
     * 装饰顺序： Fallback( TimeLimiter( CircuitBreaker( Supplier ) ) )
     */
    public <T> T executeWithCBAndTimeout(String cbName,
                                         String tlName,
                                         Supplier<T> primary,
                                         Supplier<T> fallback) {
        CircuitBreaker cb = cbRegistry.circuitBreaker(cbName);
        TimeLimiter tl = tlRegistry.timeLimiter(tlName);

        // 1. CircuitBreaker
        Supplier<T> cbSupplier = CircuitBreaker.decorateSupplier(cb, primary);

        // 2. TimeLimiter
        Supplier<T> timedSupplier = () -> {
            try {
                return tl.executeFutureSupplier(
                        () -> CompletableFuture.supplyAsync(cbSupplier, asyncExecutor)
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // 3. Fallback
        Supplier<T> decorated = Decorators.ofSupplier(timedSupplier)
                .withFallback(throwable -> fallback.get())
                .decorate();

        return decorated.get();
    }
}