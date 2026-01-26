package com.gengzi.rag.agent.reactagent.tool;


import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.SneakyThrows;
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
        String text = subReActAgent.call(request.taskDescription).getText();
        return text;
    }

    public record TaskRequest(
            @ToolParam(description = "具体的任务指令，例如：'读取 xxx 文件'") String taskDescription
    ) {
    }


}
