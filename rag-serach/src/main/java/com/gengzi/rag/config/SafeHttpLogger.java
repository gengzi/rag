package com.gengzi.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SafeHttpLogger implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger("LLM_WIRE");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        // 1. 【发请求前】直接把 byte[] 转字符串，不反序列化对象，100%安全
        String requestBody = new String(body, StandardCharsets.UTF_8);
        
        log.info("========== [LLM Request] ==========");
        log.info("URI: {}", request.getURI());
        log.info("Headers: {}", request.getHeaders());
        log.info("Payload: {}", requestBody); // 这里就是你要的完整报文
        log.info("===================================");

        // 2. 放行请求
        ClientHttpResponse response = execution.execute(request, body);

        // 3. 【收到响应后】如果需要看响应体，需要更复杂的 Buffering 配置
        // 这里仅打印状态码，防止流被耗尽
        log.info("========== [LLM Response] ==========");
        log.info("Status: {}", response.getStatusCode());
        log.info("====================================");

        return response;
    }
}