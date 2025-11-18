package com.gengzi.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomReactiveUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public JwtReactiveAuthenticationManager(JwtTokenProvider jwtTokenProvider, CustomReactiveUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        // 1. 校验认证信息是否有效（非空、已包含权限信息）
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().isEmpty()) {
            return Mono.error(new IllegalArgumentException("无效的 JWT 认证信息"));
        }

        // 2. 验证权限格式（可选，确保角色以 ROLE_ 开头，符合 Spring Security 规范）
        boolean hasValidAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .allMatch(authority -> authority.startsWith("ROLE_") || authority.startsWith("PERM_")); // 支持角色和权限
        if (!hasValidAuthorities) {
            return Mono.error(new IllegalArgumentException("JWT 中的权限格式不合法（需以 ROLE_ 或 PERM_ 开头）"));
        }

        // 3. 直接返回已认证的信息（无需密码校验，因为 JWT 已在过滤器中验证过有效性）
        return Mono.just(authentication);

    }


}
