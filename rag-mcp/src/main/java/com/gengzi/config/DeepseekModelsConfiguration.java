package com.gengzi.config;


import cn.hutool.core.util.StrUtil;
import com.gengzi.utils.ResourceUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;


/**
 * 注册deepseek所有的chatmodel
 */
@Configuration
public class DeepseekModelsConfiguration implements InitializingBean {


    private BiConsumer<String, ChatModelAndSysPrompt> registerConsumer;

    private ChatModel openAiChatModel;

    private SceneConfig sceneConfig;

    private ChatModeParamsConfig config;

    public DeepseekModelsConfiguration(ConfigurableBeanFactory beanFactory, SceneConfig sceneConfig, ChatModeParamsConfig config) {
        this.openAiChatModel = openAiChatModel;
        this.config = config;
        this.registerConsumer = (name, chatModelAndSysPrompt) -> beanFactory.registerSingleton(name.concat("ChatClientBuilder"),
                (ChatClient.create(chatModelAndSysPrompt.openAiChatModel).mutate().defaultSystem(ResourceUtil.loadFileContent(chatModelAndSysPrompt.systemPrompt))));
        this.sceneConfig = sceneConfig;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, ChatModelAndSysPrompt> chatModelMap = sceneConfig.getItems().stream()
                .filter(scene -> StrUtil.isNotBlank(scene.getName()))
                .collect(Collectors.toMap(SceneConfig.Scene::getName, scene -> {
                    OpenAiApi openApi = OpenAiApi.builder().apiKey(config.getApiKey())
                            .baseUrl(config.getBaseUrl()).build();
                    OpenAiChatModel build = OpenAiChatModel.builder()
                            .openAiApi(openApi)
                            .defaultOptions(OpenAiChatOptions.builder()
                                    .model(scene.getModelName())
                                    .temperature(config.getTemperature())
                                    .build())
                            .build();
                    return new ChatModelAndSysPrompt(build, scene.getSystemPrompt());
                }));
        chatModelMap.forEach(registerConsumer);
    }

    @Data
    @AllArgsConstructor
    public static class ChatModelAndSysPrompt {
        private OpenAiChatModel openAiChatModel;
        private String systemPrompt;

    }
}
