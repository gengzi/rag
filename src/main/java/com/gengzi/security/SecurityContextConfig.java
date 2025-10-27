package com.gengzi.security;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;


@Configuration
public class SecurityContextConfig {

    @PostConstruct
    public void init() {
        // 设置 SecurityContextHolder 使用 TTL 策略
        SecurityContextHolder.setStrategyName(TtlSecurityContextHolderStrategy.class.getName());
    }
}