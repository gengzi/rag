package com.gengzi.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 配置自定义异步执行器
    @Bean(name = "mvcTaskExecutor")
    public AsyncTaskExecutor mvcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(10);
        // 最大线程数
        executor.setMaxPoolSize(50);
        // 队列容量
        executor.setQueueCapacity(500);
        // 线程名称前缀
        executor.setThreadNamePrefix("mvc-async-");
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：当线程和队列都满时如何处理新任务
        executor.setRejectedExecutionHandler((r, executor1) -> {
            // 可以自定义处理逻辑，如记录日志或返回错误信息
            throw new RuntimeException("系统繁忙，请稍后再试");
        });
        // 初始化执行器
        executor.initialize();
        return executor;
    }

    // 将自定义执行器应用到 Spring MVC
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcTaskExecutor());
        // 设置异步请求超时时间（毫秒）
        configurer.setDefaultTimeout(30000);
    }


    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 线程池配置（核心线程数、最大线程数等）
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();

        // 用 TTL 包装线程池
        return TtlExecutors.getTtlExecutor(executor);
    }
}
