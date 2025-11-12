package com.gengzi.tool.ppt.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aippt")
@Data
public class AiPPTConfig {

    private String outlinePrompt;

    private String pageGenPrompt;

    private String humanFeedbackPrompt;

}
