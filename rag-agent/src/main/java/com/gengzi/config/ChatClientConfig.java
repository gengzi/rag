package com.gengzi.config;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Autowired
    private ChatModelConfig chatModelConfig;

    @Bean
    public ChatClient deepseekChatClientNoRag(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }


    @Bean
    public ChatClient.Builder chatClientBuilder(OpenAiChatModel chatModel) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        return  builder;
    }
}
