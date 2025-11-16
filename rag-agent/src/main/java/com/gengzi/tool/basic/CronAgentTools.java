package com.gengzi.tool.basic;


import com.alibaba.cloud.ai.graph.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CronAgentTools {
    private static final Logger logger = LoggerFactory.getLogger(CronAgentTools.class);


    /**
     * spring 会将自动将 key springbean name ，value 是当前spring 注册的 bean
     *
     * 比如 List<CompiledGraph>  也可以进行注入
     *
     */
    @Autowired(required = false)
    private Map<String, CompiledGraph> agentsMap;

    @Tool(description = "可根据用户提供的定时表达式, 创建运行相应的Agent在后台定时执行")
    public String createCornTool(
            @ToolParam(description = "定时表达式,（例如，'0 0 8 * * ?'表示每天上午8点，需要6个参数）") String cron,
            @ToolParam(description = "Agent名称来源于spring context bean 名称") String agentName
    ) {
        logger.info("创建定时任务: {} 时间: {}", agentName, cron);
        // TODO 具体添加一个定时任务
        return "成功创建一个" + cron + "的定时agent" + agentName;
    }


}
