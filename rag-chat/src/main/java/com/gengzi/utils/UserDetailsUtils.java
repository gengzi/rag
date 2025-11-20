package com.gengzi.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public class UserDetailsUtils {
    private static final Logger logger = LoggerFactory.getLogger(UserDetailsUtils.class);
    public static Mono<Object> getUserDetails() {
        // 从响应式安全上下文获取 Authentication，再提取 principal（UserDetails）
        return ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty( Mono.error(() -> new RuntimeException("未获取到用户信息")))
                .doOnNext(securityContext -> {logger.info("获取用户信息：{}", securityContext.getAuthentication().getPrincipal());})
                .doOnError( e -> logger.error("获取用户信息失败：{}", e.getMessage()))
                .map(securityContext -> securityContext.getAuthentication())
                .map(authentication ->  authentication.getPrincipal()); // 强转为自定义用户类
    }
}
