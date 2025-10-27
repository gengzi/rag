package com.gengzi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * 配置RestTemplate，设置超时时间（适配大文件/慢接口）
     */
    @Bean
    public RestTemplate restTemplate() {
        // 1. 配置请求工厂（设置超时）
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        ((SimpleClientHttpRequestFactory) factory).setConnectTimeout(100000); // 连接超时：100秒
        ((SimpleClientHttpRequestFactory) factory).setReadTimeout(1200000);  // 读取超时：20分钟（处理PDF解析）

        // 2. 创建RestTemplate并注入工厂
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // （可选）添加拦截器（如日志打印、Token附加等）
        // restTemplate.setInterceptors(Collections.singletonList(new LoggingRequestInterceptor()));
        
        return restTemplate;
    }
}