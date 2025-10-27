package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 单页的Markdown结果（对应LayoutParsingPageItem的markdown字段）
 */
@Data
public class LayoutMarkdownResult {

    /**
     * Markdown文本内容
     * 说明：当前页版面解析后的结构化文本（如标题、段落、表格等）
     */
    @JsonProperty("text")
    private String text;

    /**
     * Markdown图片映射
     * 说明：key=图片相对路径（如"img/1.jpg"），value=JPEG格式的Base64字符串
     */
    @JsonProperty("images")
    private Map<String, String> images;

    /**
     * 当前页第一个元素是否为段落开始
     * 说明：用于多页文档的段落连贯性判断
     */
    @JsonProperty("isStart")
    private Boolean isStart;

    /**
     * 当前页最后一个元素是否为段落结束
     * 说明：用于多页文档的段落连贯性判断
     */
    @JsonProperty("isEnd")
    private Boolean isEnd;

}