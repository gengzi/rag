package com.gengzi.config;


import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModeConfig {

    @Autowired
    private OpenAiChatModel openAiChatModel;



}
