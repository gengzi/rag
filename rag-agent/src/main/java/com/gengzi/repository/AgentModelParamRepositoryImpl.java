package com.gengzi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.util.ResourceUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public class AgentModelParamRepositoryImpl implements AgentModelParamRepository {

    private final List<AgentModel> agentModels;

    public AgentModelParamRepositoryImpl(@Value("classpath:agent_model_config.json") Resource agentsConfig, ObjectMapper objectMapper) {
        try {
            agentModels = objectMapper.readValue(ResourceUtil.loadResourceAsString(agentsConfig),
                    new TypeReference<List<AgentModel>>() {
                    });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AgentModel> loadModels() {
        return agentModels;
    }


    public record AgentModel(String name, String modelName) {

    }

}
