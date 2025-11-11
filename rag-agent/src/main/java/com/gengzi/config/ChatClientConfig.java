package com.gengzi.config;


import com.gengzi.advisor.UpdateOnlyMessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Autowired
    private ChatModelConfig chatModelConfig;

    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;

    @Bean
    public ChatClient deepseekChatClientNoRag(OpenAiChatModel chatModel) {
//        MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        UpdateOnlyMessageChatMemoryAdvisor updateOnlyMessageChatMemoryAdvisor = UpdateOnlyMessageChatMemoryAdvisor.builder(chatMemory).build();
        return ChatClient.builder(chatModel).defaultAdvisors(updateOnlyMessageChatMemoryAdvisor).build();
    }


    @Bean
    public ChatClient.Builder chatClientBuilder(OpenAiChatModel chatModel) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        return builder;
    }
}
