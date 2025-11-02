package com.gengzi.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Component
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtReactiveAuthenticationManager(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.justOrEmpty(authentication.getCredentials())
                .cast(String.class)
                .filter(token -> jwtTokenProvider.validateToken(token)) // 验证 JWT 有效性
                .switchIfEmpty(Mono.error(new RuntimeException("无效的 JWT 令牌")))
                .map(token -> {
                    // 从 JWT 中提取用户名和角色
                    String username = jwtTokenProvider.getUsernameFromJWT(token);
//                    String role = jwtTokenProvider.extractRole(token);
                    // 创建认证信息（包含角色权限）
                    return new UsernamePasswordAuthenticationToken(
                            username,
                            token,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                });
    }
}