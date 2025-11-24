package com.gengzi.rag.agent.reactagent.tool;


import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.function.Function;


public class PPTMotherboadrToolV2 implements Function<PPTMotherboadrToolV2.MotherboadrType, PPTMotherboadrToolV2.Motherboadr> {


    /**
     * Applies this function to the given argument.
     *
     * @param  the function argument
     * @return the function result
     */
    @Override
    public Motherboadr apply(@ToolParam(description = "ppt的类型") MotherboadrType type) {
        // 查询数据库获取当前的母版名称集合
        Motherboadr motherboadr = new Motherboadr(List.of("母版类型-1"));
        return motherboadr;
    }

    public record MotherboadrType(
            @ToolParam(description = "ppt的类型") String motherboadrName
    ) {
    }


    public record Motherboadr(
            @ToolParam(description = "可用的ppt母版名称集合") List<String> motherboadrName
    ) {
    }

}
