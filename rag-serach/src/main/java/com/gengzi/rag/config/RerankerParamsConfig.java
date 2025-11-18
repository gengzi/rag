package com.gengzi.rag.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rerankermodel")
@Data
public class RerankerParamsConfig {

    private String model;
    private String apiKey;
    private String baseUrl;
    private String instruction;

}
