package com.gengzi.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> {
                    // 1. 调整字符串解码器的缓冲（处理文本响应，若接口返回文本流）
                    configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024); // 改为1MB（根据需求调整，如5MB写5*1024*1024）
                }).
                clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)  // 连接超时
                        .responseTimeout(Duration.ofMinutes(5))  // 响应超时（长文本需更长）
                )).build();
    }
}