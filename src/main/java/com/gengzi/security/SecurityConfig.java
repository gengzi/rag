package com.gengzi.security;


import com.gengzi.ui.service.RagUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true,
        prePostEnabled = true
)
public class SecurityConfig {


    // 注入自定义的UserDetailsService
    private final UserDetailsService userDetailsService;


    public SecurityConfig(RagUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }


    // 1. 配置安全过滤链（核心）：定义 URL 访问规则、登录/退出行为等
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关闭 CSRF（开发环境可关闭，生产环境需根据场景决定）
                .csrf(csrf -> csrf.disable())
                // 1. 允许跨域请求（关键配置）
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .userDetailsService(userDetailsService)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 无状态，禁用 session
                )
                // 关闭默认的表单登录（JWT 认证不需要）
                .formLogin(form -> form.disable())
                // 配置 URL 访问权限
                .authorizeHttpRequests(auth -> auth
                        // 允许匿名访问的路径
                        .requestMatchers("/", "/home", "/v3/api-docs/**",
                                "/s3Management/**", "/swagger-ui/**", "/swagger-ui.html", "/user/login").permitAll()
                        // 管理员才能访问的路径
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 其他所有路径需要认证
                        .anyRequest().authenticated()
                );
        // 配置表单登录
//                .formLogin(form -> form
//                        .loginPage("/login") // 自定义登录页
//                        .defaultSuccessUrl("/dashboard", true) // 登录成功后跳转
//                        .permitAll() // 允许匿名访问登录页
//                )
        // 配置退出登录
//                .logout(logout -> logout
//                        .logoutSuccessUrl("/login?logout") // 退出后跳转
//                        .permitAll()
//                );
        // 添加自定义JWT过滤器
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 定义 AuthenticationManager Bean
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


    /**
     * 配置跨域规则（与全局配置类似）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("*")); // 允许的域名
        config.setAllowedHeaders(Arrays.asList("*"));        // 允许的请求头
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // 允许的方法
        config.setAllowCredentials(true);                    // 允许携带 Cookie
        config.setMaxAge(3600L);                            // 预检请求有效期

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


}
