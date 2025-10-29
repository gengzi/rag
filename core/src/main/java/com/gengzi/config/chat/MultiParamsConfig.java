package com.gengzi.config.chat;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "multimodel")
@Data
public class MultiParamsConfig {

    private String model;
    private String apiKey;
    private String baseUrl;
}
