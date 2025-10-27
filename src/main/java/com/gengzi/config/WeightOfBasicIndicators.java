package com.gengzi.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "weight-of-basic-indicators")
@EnableConfigurationProperties(WeightOfBasicIndicators.class)
@Data
public class WeightOfBasicIndicators {

    private String faithfulness;
    private String answerRelevancy;
    private String answerSimilarity;
    private String contextRecall;
    private String contextPrecision;
    private String contextRelevancy;


}
