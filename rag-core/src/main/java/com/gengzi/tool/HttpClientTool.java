package com.gengzi.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 客户端工具 - 调用外部 REST API
 */
@Component
public class HttpClientTool {

    private final RestTemplate restTemplate;

    public HttpClientTool() {
        this.restTemplate = new RestTemplate();
    }

    @Tool(description = "发送 GET 请求到指定 URL，获取响应内容")
    public String httpGet(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "URL 不能为空";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Spring-AI-Agent/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);

            return "HTTP GET 响应 (" + response.getStatusCode() + "):\n\n" + response.getBody();

        } catch (Exception e) {
            return "HTTP GET 请求失败: " + e.getMessage();
        }
    }

    @Tool(description = "发送 POST 请求到指定 URL，可以传递 JSON 数据")
    public String httpPost(String url, String jsonBody) {
        if (url == null || url.trim().isEmpty()) {
            return "URL 不能为空";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Spring-AI-Agent/1.0");

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            return "HTTP POST 响应 (" + response.getStatusCode() + "):\n\n" + response.getBody();

        } catch (Exception e) {
            return "HTTP POST 请求失败: " + e.getMessage();
        }
    }

    @Tool(description = "检查 URL 是否可访问（ping 检测）")
    public String checkUrlAvailability(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "URL 不能为空";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Spring-AI-Agent/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.HEAD,
                    entity,
                    String.class);
            long endTime = System.currentTimeMillis();

            return String.format("✓ URL 可访问\n状态码: %s\n响应时间: %d ms",
                    response.getStatusCode(),
                    (endTime - startTime));

        } catch (Exception e) {
            return "✗ URL 不可访问: " + e.getMessage();
        }
    }

    @Tool(description = "获取 URL 的响应头信息")
    public String getResponseHeaders(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "URL 不能为空";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Spring-AI-Agent/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.HEAD,
                    entity,
                    String.class);

            StringBuilder result = new StringBuilder();
            result.append("响应头信息:\n\n");

            HttpHeaders responseHeaders = response.getHeaders();
            responseHeaders.forEach((key, value) -> result.append(key).append(": ").append(value).append("\n"));

            return result.toString();

        } catch (Exception e) {
            return "获取响应头失败: " + e.getMessage();
        }
    }
}
