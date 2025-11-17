package com.gengzi.mcp.controller;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient deepseekChatClientNoRag(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultAdvisors().build();
    }


    @Bean
    public ChatClient.Builder deepseekChatClientBuilder(OpenAiChatModel chatModel) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        return builder;
    }
}
