package com.gengzi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 开启Spring异步支持
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // 无需额外代码，@EnableAsync 注解会自动注册异步执行器
    // （可选）若需自定义线程池，可在此配置 ThreadPoolTaskExecutor，避免默认线程池耗尽

    @Bean(name = "asyncTaskExecutor")
    public ThreadPoolTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6); // 核心线程数
        executor.setMaxPoolSize(12); // 最大线程数
        executor.setQueueCapacity(100); // 队列容量
        executor.setThreadNamePrefix("Async-Parsing-"); // 线程名前缀（便于日志排查）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略（主线程执行，避免任务丢失）
        return executor;
    }
}