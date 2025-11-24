package com.gengzi.rag.agent.reactagent.tool;


import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.function.Supplier;


public class PPTMotherboadrTool implements Supplier<PPTMotherboadrTool.Motherboadr> {


    /**
     * Gets a result.
     *
     * @return a result
     */
    @Override
    public Motherboadr get() {
        // 查询数据库获取当前的母版名称集合
        Motherboadr motherboadr = new Motherboadr(List.of("母版类型-1"));
        return motherboadr;
    }


    public record Motherboadr(
            @ToolParam(description = "可用的ppt母版名称集合") List<String> motherboadrName
    ) {
    }

}
