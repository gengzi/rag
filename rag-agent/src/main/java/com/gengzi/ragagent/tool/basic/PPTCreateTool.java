package com.gengzi.ragagent.tool.basic;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;


/**
 * ppt创建工具
 */
public class PPTCreateTool implements BiFunction<String, ToolContext,String> {
    /**
     * Applies this function to the given arguments.
     *
     * @param pptOutline           the first function argument
     * @param toolContext the second function argument
     * @return the function result
     */
    @Override
    public String apply(@ToolParam(description = "需要生成的ppt大纲内容") String pptOutline, ToolContext toolContext) {


        return "xxx";
    }
}
