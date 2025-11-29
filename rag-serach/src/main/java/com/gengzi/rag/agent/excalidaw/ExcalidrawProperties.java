package com.gengzi.rag.agent.excalidaw;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "excalidraw")
@Data
public class ExcalidrawProperties {

    /**
     * 系统提示词文件路径，例如：prompts/agent/sys_excalidraw_prompt.md
     */
    private String sysPrompt;


}