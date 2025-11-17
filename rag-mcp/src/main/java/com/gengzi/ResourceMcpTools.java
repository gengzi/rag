package com.gengzi;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 */
@Component
public class ResourceMcpTools {
    private static final Logger logger = LoggerFactory.getLogger(ResourceMcpTools.class);


    @McpResource(name = "user_file", description = "提供用户的个人文件", uri = "user-profile://{username}")
    public McpSchema.ReadResourceResult searchMemory(@McpArg(name = "username", description = "用户名", required = true) String username) {
        String profileData = "我叫张三";
        return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                        "user-profile://" + username,
                        "application/json",
                        profileData)
        ));
    }


    @McpPrompt(
            name = "greeting",
            description = "生成问候语")
    public McpSchema.GetPromptResult greeting(
            @McpArg(name = "name", description = "用户名", required = true)
            String name) {

        String message = "你好, " + name + "! 今天心情怎么样?";

        return new McpSchema.GetPromptResult(
                "Greeting",
                List.of(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent(message)))
        );
    }

    @McpComplete(prompt = "city-search")
    public List<String> completeCityName(String prefix) {
        return List.of("上海", "北京", "广州", "深圳");
    }


}
