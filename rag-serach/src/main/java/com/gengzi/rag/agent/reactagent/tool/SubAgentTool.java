package com.gengzi.rag.agent.reactagent.tool;


import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.SneakyThrows;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Function;


@Component
public class SubAgentTool implements Function<SubAgentTool.TaskRequest, String> {

    @Autowired
    private ReactAgent subReActAgent;


    @SneakyThrows
    @Override
    public String apply(@ToolParam(description = "具体任务") TaskRequest request) {
        UserMessage userMessage = new UserMessage("上下文信息:" + request.context + "\n\n从上下文信息完成任务:" + request.taskCommand);
        String text = subReActAgent.call(userMessage).getText();
        return text;
    }

    public record TaskRequest(
            @ToolParam(description = "具体的任务指令，例如：'读取 xxx 地址的文件，修改 xx 文件的内容'") String taskCommand,
            @ToolParam(description = "任务指令可参考上下文信息,例如：文件内容，用户偏好") String context
    ) {
    }


}
