package com.gengzi.agent;


import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.gengzi.tool.basic.CronAgentTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class CronAgentConfiguration {

    @Autowired
    private CronAgentTools cronAgentTools;

    @Bean
    public ReactAgent createCronAgent(CronAgentTools cronAgentTools, @Qualifier("openAiChatModel") ChatModel chatModel) throws GraphStateException {
        ToolCallback[] dateTimeTools = ToolCallbacks.from(cronAgentTools);
        List<ToolCallback> collect = Arrays.stream(dateTimeTools).collect(Collectors.toList());
        ReactAgent reactAgent = ReactAgent.builder().name("cronAgent").description("用于帮助用户设置定时任务的agent")
                .tools(collect)
                .model(chatModel)
                .inputKey("agent_input")
                .outputKey("agent_output")
                .instruction("可按用户提供的定时或周期性执行指令，解析出符合Quartz格式的Cron表达式并调用工具方法，帮助用户创建一个异步定时运行的Agent")
                .build();
        return reactAgent;
    }


}
