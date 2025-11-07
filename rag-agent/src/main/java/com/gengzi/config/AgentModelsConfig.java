package com.gengzi.config;

import com.gengzi.repository.AgentModelParamRepository;
import com.gengzi.repository.AgentModelParamRepositoryImpl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;


/**
 * 通过配置文件创建不同的{@link ChatClient.Builder} 名称的实例
 * 主要为了避免重复创建 ChatClient.Builder ，因为每个agent 的模型配置可能不同
 */
@Configuration
public class AgentModelsConfig implements InitializingBean {

    private static final String BEAN_NAME_SUFFIX = "ChatClientBuilder";
    private List<AgentModelParamRepositoryImpl.AgentModel> agentModels;

    private BiConsumer<String, ChatModel> registerConsumer;

    private ChatModeParamsConfig config;


    public AgentModelsConfig(ConfigurableBeanFactory beanFactory, AgentModelParamRepository agentModelParamRepository, ChatModeParamsConfig config) {
        this.config = config;
        agentModels = agentModelParamRepository.loadModels();
        this.registerConsumer = (key, value) -> beanFactory.registerSingleton(key.concat(BEAN_NAME_SUFFIX),
                ChatClient.create(value).mutate());
    }

    private Map<String, ChatModel> agentModels() {
        return agentModels.stream().map(agentModel -> {
            OpenAiApi openApi = OpenAiApi.builder().apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl()).build();
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .openAiApi(openApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(agentModel.modelName())
                            .temperature(config.getTemperature())
                            .build())
                    .build();
            return Map.entry(agentModel.name(), model);
        }).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }

    @Override
    public void afterPropertiesSet() {
        this.agentModels().forEach(registerConsumer);
    }
}
