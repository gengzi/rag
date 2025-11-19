//package com.gengzi.security;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import org.springframework.web.server.WebFilter;
//import org.springframework.web.server.WebFilterChain;
//import reactor.core.publisher.Mono;
//
//@Component
//public class SecurityContextCheckFilter implements WebFilter {
//    private static final Logger logger = LoggerFactory.getLogger(SecurityContextCheckFilter.class);
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        // 在认证过滤器之后执行（顺序在 AUTHENTICATION 之后）
//        return chain.filter(exchange)
//                .then(Mono.defer(() -> {
//                    // 从 exchange 中获取 SecurityContext
//                    return exchange.getPrincipal()
//                            .doOnNext(principal -> {
//                                logger.info("ServerWebExchange 中获取到用户：{}", principal);
//                            })
//                            .then();
//                }));
//    }
//}