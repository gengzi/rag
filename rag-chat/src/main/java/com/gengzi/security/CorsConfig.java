package com.gengzi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的源（前端地址），生产环境不要用 "*"
        config.setAllowedOriginPatterns(Arrays.asList("http://localhost:*"));

        // 允许的 HTTP 方法
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 允许的请求头
        config.setAllowedHeaders(Arrays.asList("*"));

        // 是否允许携带 Cookie（如果用 JWT 通常不需要）
        config.setAllowCredentials(true);

        // 暴露给前端的响应头（如自定义 token 头）
        config.setExposedHeaders(Arrays.asList("Authorization", "X-Custom-Token"));

        // 应用到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}