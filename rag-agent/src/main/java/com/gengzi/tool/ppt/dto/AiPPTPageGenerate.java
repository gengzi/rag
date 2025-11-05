package com.gengzi.tool.ppt.dto;


import lombok.Data;

import java.util.List;

@Data
public class AiPPTPageGenerate {

    /**
     * 页面类型
     */
    private String pageType;


    /**
     * 大纲信息
     * 总标题
     * 章节
     * 小结
     */
    private Object outlineInfo;


    /**
     * 占位符元素信息
     */
    private List<AiPPTPlaceholder> placeholders;


}
