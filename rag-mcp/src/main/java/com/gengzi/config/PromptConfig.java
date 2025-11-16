package com.gengzi.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromptConfig {

    @Bean
    public ChatClient queryExpanderChatClient(ChatClient.Builder queryExpanderChatClientBuilder) {
        return queryExpanderChatClientBuilder.build();

    }

}
