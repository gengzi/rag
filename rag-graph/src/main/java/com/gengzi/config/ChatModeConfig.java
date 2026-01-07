package com.gengzi.config;


import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 大模型配置类
 * 配置 ChatModel Bean 用于图谱提取等大模型调用
 */
@Configuration
public class ChatModeConfig {

    /**
     * 获取 ChatModel Bean
     * Spring AI 会自动根据 application.yml 中的配置创建 OpenAiChatModel
     */
    @Bean
    public ChatModel chatModel(ChatModel openAiChatModel) {
        return openAiChatModel;
    }

}
