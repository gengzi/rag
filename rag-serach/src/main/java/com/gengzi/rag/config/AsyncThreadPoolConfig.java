package com.gengzi.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * 用于 IO 密集型任务（如摘要生成、LLM 调用等）
 *
 * @author: gengzi
 */
@Configuration
@EnableAsync
public class AsyncThreadPoolConfig {

    /**
     * IO 密集型任务线程池
     * 核心线程数 = CPU 核心数 * 2
     * 最大线程数 = CPU 核心数 * 3
     */
    @Bean(name = "ioTaskExecutor")
    public Executor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 获取 CPU 核心数
        int processors = Runtime.getRuntime().availableProcessors();

        // IO 密集型：核心线程数 = CPU 核心数 * 2
        executor.setCorePoolSize(processors * 2);

        // 最大线程数 = CPU 核心数 * 3
        executor.setMaxPoolSize(processors * 3);

        // 队列容量
        executor.setQueueCapacity(200);

        // 线程名称前缀
        executor.setThreadNamePrefix("io-task-");

        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);

        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true);

        // 等待所有任务完成后关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60);

        // 初始化
        executor.initialize();

        return executor;
    }

    /**
     * 摘要任务专用线程池
     * 用于聊天摘要生成任务
     */
    @Bean(name = "summaryTaskExecutor")
    public Executor summaryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int processors = Runtime.getRuntime().availableProcessors();

        // 摘要任务：核心线程数 = CPU 核心数
        executor.setCorePoolSize(processors);

        // 最大线程数 = CPU 核心数 * 2
        executor.setMaxPoolSize(processors * 2);

        // 队列容量（摘要任务较少）
        executor.setQueueCapacity(50);

        // 线程名称前缀
        executor.setThreadNamePrefix("summary-task-");

        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 线程空闲时间
        executor.setKeepAliveSeconds(120);

        // 允许核心线程超时
        executor.setAllowCoreThreadTimeOut(true);

        // 等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        return executor;
    }
}
