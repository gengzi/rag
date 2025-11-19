package com.gengzi.security;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager=true) // 支持响应式方法级安全（如 @PreAuthorize）
public class SecurityConfig {


    private final JwtReactiveAuthenticationManager authenticationManager;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private CustomReactiveUserDetailsService customReactiveUserDetailsService;

//    @Autowired
//    private SecurityContextCheckFilter securityContextCheckFilter;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtReactiveAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    // 配置安全规则
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable()) // WebFlux 中 CSRF 可禁用（视场景而定）
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/login", "/v3/api-docs/**",
                                "/s3Management/**", "/swagger-ui/**", "/swagger-ui.html", "/user/login", "/webjars/**").permitAll() // 登录接口允许匿名访问
                        .pathMatchers("/admin/**").hasRole("ADMIN") // 管理员接口需 ADMIN 角色
                        .anyExchange().authenticated() // 其他接口需认证
                )
                .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION) // 添加 JWT 过滤器
//                .addFilterAfter(securityContextCheckFilter, SecurityWebFiltersOrder.AUTHENTICATION) // 认证后检查
                // 禁用默认的表单登录和 HTTP Basic，避免冲突
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
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
        return exchange -> {
            // 从请求头 "Authorization" 中获取 Token（格式：Bearer <token>）
            String token = exchange.getRequest().getHeaders()
                    .getFirst("Authorization");

            if (token == null || !token.startsWith("Bearer ")) {
                return Mono.empty(); // 无 Token 则返回空（后续会被判定为未认证）
            }

            String jwt = token.substring(7); // 去除 "Bearer " 前缀
            // 验证 Token 并解析用户名
            return Mono.fromCallable(()->jwtTokenProvider.validateToken(jwt))
                    .filter(valid -> valid)
                    .flatMap(valid -> {  // 从 Token 中直接解析用户名和角色
                        String username = jwtTokenProvider.getUsernameFromJWT(jwt);
                        Claims claimsFromToken = jwtTokenProvider.getClaimsFromToken(jwt);
                        List<String> roles = claimsFromToken.get("roles", List.class);
                        // 构建权限集合（角色需要以 "ROLE_" 为前缀）
                        Collection<GrantedAuthority> authorities = roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList());

                        // 构建认证信息（无需查询数据库）
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                username, // 用户名（可替换为自定义用户对象）
                                null, // 密码无需存储在认证信息中
                                authorities // 从 Token 解析的角色权限
                        );
                        return Mono.just(auth).doOnNext(a -> System.out.println(">>> Authentication set: " + a));
                    });
        };
    }
}