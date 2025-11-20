package com.gengzi.rag.agent.deepresearch.tool;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class PlannerTool {

    private static final Logger logger = LoggerFactory.getLogger(PlannerTool.class);

    /**
     * 定义了一个工具，此工具不会执行任何代码，只是用来判断llm判断当前用户聊天是否需要进行深度检索
     * @param taskTitle
     */
    @Tool(name = "handoff_to_planner", description = "这是一个专业规划师，相关任务都可以交给他处理")
    public void handoffToPlanner(String taskTitle) {
        // This method is not returning anything. It is used as a way for LLM
        // to signal that it needs to hand off to the planner agent.
        logger.info("🔧 Handoff to planner task: {}", taskTitle);
    }
}
