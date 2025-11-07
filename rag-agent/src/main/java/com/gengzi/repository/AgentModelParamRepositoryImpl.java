package com.gengzi.repository;

import cn.hutool.json.JSONUtil;
import com.gengzi.util.ResourceUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public class AgentModelParamRepositoryImpl implements AgentModelParamRepository {

    private final List<AgentModel> agentModels;

    public AgentModelParamRepositoryImpl(@Value("classpath:agent_model_config.json") Resource agentsConfig) {
        agentModels = JSONUtil.toList(ResourceUtil.loadResourceAsString(agentsConfig), AgentModel.class);
    }

    @Override
    public List<AgentModel> loadModels() {
        return agentModels;
    }


    public record AgentModel(String name, String modelName) {

    }

}
