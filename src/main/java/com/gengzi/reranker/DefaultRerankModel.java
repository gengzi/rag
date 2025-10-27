package com.gengzi.reranker;

import com.gengzi.reranker.request.RerankInstructions;
import com.gengzi.reranker.request.RerankModelRequest;
import com.gengzi.reranker.response.RerankResponse;
import com.gengzi.reranker.response.RerankerModelResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

public class DefaultRerankModel implements RerankerModel {


    private final RestClient restClient;
    private final String apiKey; // Silicon Flow 的 API Token
    private final String baseUrl;
    private final String model;

    // 构造方法：初始化 RestClient 并传入 API Key
    public DefaultRerankModel(String apiKey, String baseUrl,String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl) // API 基础地址
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", "Bearer " + apiKey) // 认证头
                .build();
    }


    /**
     * @param request the request object to be sent to the AI model
     * @return
     */
    @Override
    public RerankerModelResponse call(RerankModelRequest request) {
        RerankInstructions instructions = request.getInstructions();
        instructions.setModel(model);

        // 2. 发送 POST 请求到 rerank API
        RerankResponse apiResponse = restClient.post()
                .uri("/v1/rerank") // 接口路径
                .body(instructions) // 请求体
                .retrieve()
                .body(RerankResponse.class); // 响应类型
        return new RerankerModelResponse(apiResponse);
    }
}
