package com.gengzi.rag.config;


import com.gengzi.rag.reranker.DefaultRerankModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 自定义构建对话模型配置
 */
@Configuration
public class ChatModelConfig {


    @Autowired
    private ChatModeParamsConfig config;

    @Autowired
    private RerankerParamsConfig rerankerParamsConfig;


    @Bean
    @Qualifier("openAiChatModel")
    public OpenAiChatModel openAiChatModel() {
        OpenAiApi openApi = OpenAiApi.builder().apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl()).build();
        return OpenAiChatModel.builder()
                .openAiApi(openApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModel())
                        .temperature(config.getTemperature())
                        .build())
                .build();
    }


    @Bean
    public DefaultRerankModel defaultRerankModel() {
        return new DefaultRerankModel(rerankerParamsConfig.getApiKey(), rerankerParamsConfig.getBaseUrl(), rerankerParamsConfig.getModel());
    }


    @Bean
    public OpenAiChatModel.Builder openAiChatModelBuilder() {
        OpenAiApi openApi = OpenAiApi.builder().apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl()).build();
        OpenAiChatModel.Builder builder = OpenAiChatModel.builder()
                .openAiApi(openApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModel())
                        .temperature(config.getTemperature())
                        .build());
        return builder;
    }




}
