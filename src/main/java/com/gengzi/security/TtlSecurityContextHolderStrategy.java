package com.gengzi.security;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

public class TtlSecurityContextHolderStrategy implements SecurityContextHolderStrategy {

    // 使用 TTL 的 TransmittableThreadLocal 存储上下文
    private final TransmittableThreadLocal<SecurityContext> contextHolder = new TransmittableThreadLocal<>();

    @Override
    public void clearContext() {
        contextHolder.remove();
    }

    @Override
    public SecurityContext getContext() {
        SecurityContext context = contextHolder.get();
        if (context == null) {
            context = createEmptyContext();
            contextHolder.set(context);
        }
        return context;
    }

    @Override
    public void setContext(SecurityContext context) {
        contextHolder.set(context);
    }

    @Override
    public SecurityContext createEmptyContext() {
        return new org.springframework.security.core.context.SecurityContextImpl();
    }
}