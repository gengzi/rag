package com.gengzi.config.chat;


import com.gengzi.advisor.MessageChatRecordAdvisor;
import com.gengzi.dao.repository.ConversationRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Autowired
    @Qualifier("ragAdvisor")
    private Advisor advisor;

    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory chatMemory;


    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    @Qualifier("ragPromptAdvisor")
    private Advisor ragPromptChatMemoryAdvisor;

    @Autowired
    private ChatModelConfig chatModelConfig;

    /**
     * 执行rag流程
     * <p>
     * 聊天记忆advisor
     * 聊天记录advisor
     * rag增强advisor
     *
     * @param chatModel
     * @return
     */
    @Bean
    public ChatClient deepseekChatClientByRag(OpenAiChatModel chatModel) {
        MessageChatRecordAdvisor messageChatRecordAdvisor = new MessageChatRecordAdvisor(conversationRepository);
        return ChatClient.builder(chatModel).defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                messageChatRecordAdvisor, advisor).build();
    }

    @Bean
    public ChatClient deepseekChatClientByRagSearch(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultAdvisors(advisor).build();
    }

    @Bean
    public ChatClient deepseekChatClientNoRag(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }


    /**
     * 多模态模型- 比如对图片进行语义的识别
     *
     * @return
     */
    @Bean
    public ChatClient chatClientImage() {
        return ChatClient.builder(chatModelConfig.openAiChatModelImage()).build();
    }


    /**
     * 不执行rag流程
     *
     * @param chatModel
     * @return
     */
    @Bean
    public ChatClient deepseekChatClient(OpenAiChatModel chatModel) {
        MessageChatRecordAdvisor messageChatRecordAdvisor = new MessageChatRecordAdvisor(conversationRepository);
        return ChatClient.builder(chatModel).defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                messageChatRecordAdvisor
        ).build();

    }


}
