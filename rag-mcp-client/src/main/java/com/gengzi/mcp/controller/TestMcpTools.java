package com.gengzi.mcp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;


@Service
public class TestMcpTools {
    private static final Logger logger = LoggerFactory.getLogger(TestMcpTools.class);

    /**
     * 存储用户记忆
     */
    @Tool(name = "print", description = "用于记录用户发送的信息")
    public String storeMemory(
            @ToolParam(description = "信息") String text, ToolContext context) {
        logger.info("text: {} , context：{}",text,context.getContext().get("username"));
        return  context.toString();
    }

}
