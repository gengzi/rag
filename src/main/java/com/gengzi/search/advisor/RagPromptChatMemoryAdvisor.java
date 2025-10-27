package com.gengzi.search.advisor;


import com.gengzi.search.template.RagPromptTemplate;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class RagPromptChatMemoryAdvisor {


    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;

    @Bean("ragPromptAdvisor")
    public Advisor ragPromptChatMemoryAdvisor() {

//         return PromptChatMemoryAdvisor.builder(chatMemory).systemPromptTemplate(
//            new PromptTemplate("")
//         ).build();

        return PromptChatMemoryAdvisor.builder(chatMemory).build();
    }

}
