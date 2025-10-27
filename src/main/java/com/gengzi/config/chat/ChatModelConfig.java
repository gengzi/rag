package com.gengzi.config.chat;

import com.gengzi.reranker.DefaultRerankModel;
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

    @Autowired
    private MultiParamsConfig multiParamsConfig;

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


    public OpenAiChatModel openAiChatModelImage() {
        OpenAiApi openApi = OpenAiApi.builder().apiKey(multiParamsConfig.getApiKey())
                .baseUrl(multiParamsConfig.getBaseUrl()).build();
        return OpenAiChatModel.builder()
                .openAiApi(openApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(multiParamsConfig.getModel())
                        .build())
                .build();
    }




//    @Bean("openAiChatModelOutPutJson")
//    public OpenAiChatModel openAiChatModelOutPutJson() {
//        OpenAiApi openApi = OpenAiApi.builder().apiKey(config.getApiKey())
//                .baseUrl(config.getBaseUrl()).build();
//        return OpenAiChatModel.builder()
//                .openAiApi(openApi)
//                .defaultOptions(OpenAiChatOptions.builder()
//                        .model(config.getModel())
//                        // 输出模板设置为json
//                        .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
//                        .temperature(config.getTemperature())
//                        .build())
//                .build();
//    }

}
