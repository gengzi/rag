package com.gengzi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "scenes") // 对应 YAML 中的 "users" 前缀
public class SceneConfig {

    private List<Scene> items;


    @Data
    public static class Scene {
        private String name;

        private String modelName;

        private String systemPrompt;
    }
}
