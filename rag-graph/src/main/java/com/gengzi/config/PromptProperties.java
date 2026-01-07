package com.gengzi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 提示词配置属性类
 * 从配置文件中加载各种提示词路径
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.prompt")
public class PromptProperties {

    /**
     * 实体识别提示词文件路径（预留）
     */
    private String entityRecognition = "prompt/ENTITY_RECOGNITION_PROMPT.md";

    /**
     * 加载实体识别提示词内容
     */
    public String loadEntityRecognitionPrompt() {
        return loadPromptFromFile(entityRecognition);
    }

    /**
     * 从文件加载提示词内容
     */
    private String loadPromptFromFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载提示词文件: " + path, e);
        }
    }
}
