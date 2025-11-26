package com.gengzi.aop;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的 key，支持 SpEL 表达式，例如： "lock:chat:create:#userId"
     */
    String key();

    /**
     * 等待获取锁的最长时间（毫秒），-1 表示不等待（立即失败）
     */
    long waitTime() default 5000; // 默认等 5 秒

    /**
     * 锁持有超时时间（毫秒），防止死锁
     */
    long leaseTime() default 30000; // 默认 30 秒自动释放

    /**
     * 时间单位（默认毫秒）
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 获取锁失败时抛出的异常信息
     */
    String errorMsg() default "系统繁忙，请稍后再试";
}