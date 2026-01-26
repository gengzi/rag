package com.gengzi.rag.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public RestClientCustomizer aiRestClientCustomizer() {
        return restClientBuilder -> {
            // 这行代码会把拦截器注入到你那段代码使用的 restClient 中
            restClientBuilder.requestInterceptor(new SafeHttpLogger());
        };
    }
}