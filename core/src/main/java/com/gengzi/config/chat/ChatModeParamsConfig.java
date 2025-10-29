package com.gengzi.config.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chatmodel")
@Data
public class ChatModeParamsConfig {

    private String model;
    private double temperature;
    private String apiKey;
    private String baseUrl;


}
