package com.air.memory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池提升查询性能
 */
@Configuration
@EnableAsync  // 启用 Spring 异步支持
public class AsyncThreadConfig {

    /**
     * IO 密集型线程池 —— 用于知识搜索、LLM 清洗等耗时操作
     * 核心线程数 = CPU核数 * 2，最大线程数 = CPU核数 * 4
     */
    @Bean("ioExecutor")
    public Executor ioExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cores * 2);
        executor.setMaxPoolSize(cores * 4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("memory-io-");
        // 拒绝策略：由调用方线程执行（防止任务丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 轻量型线程池 —— 用于记忆写回、行为记录等快速操作
     */
    @Bean("lightExecutor")
    public Executor lightExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("memory-light-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}