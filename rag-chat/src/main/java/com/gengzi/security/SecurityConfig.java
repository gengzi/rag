package com.gengzi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

@Configuration
@EnableReactiveMethodSecurity // 支持响应式方法级安全（如 @PreAuthorize）
public class SecurityConfig {

    private final JwtReactiveAuthenticationManager authenticationManager;

    public SecurityConfig(JwtReactiveAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    // 配置安全规则
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable()) // WebFlux 中 CSRF 可禁用（视场景而定）
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/login").permitAll() // 登录接口允许匿名访问
                        .pathMatchers("/admin/**").hasRole("ADMIN") // 管理员接口需 ADMIN 角色
                        .anyExchange().authenticated() // 其他接口需认证
                )
                .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION) // 添加 JWT 过滤器
                .build();
    }

    // 创建 JWT 认证过滤器
    private AuthenticationWebFilter jwtAuthenticationFilter() {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(authenticationManager);
        // 指定过滤器作用的路径（所有请求）
        filter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/**"));
        // 设置从请求中提取 JWT 的转换器
        filter.setServerAuthenticationConverter(jwtServerAuthenticationConverter());
        return filter;
    }

    // 从请求头中提取 JWT（格式：Bearer <token>）
    private ServerAuthenticationConverter jwtServerAuthenticationConverter() {
        return exchange -> Mono.justOrEmpty(
                exchange.getRequest()
                        .getHeaders()
                        .getFirst("Authorization")
        ).filter(authHeader -> authHeader.startsWith("Bearer "))
                .map(authHeader -> authHeader.substring(7)) // 去除 "Bearer " 前缀
                .map(token -> new UsernamePasswordAuthenticationToken(token, token)); // 封装为未认证 token
    }
}