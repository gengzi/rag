package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 解析结果：按阅读顺序排列的单个版面区域内容
 */
@Data
public class ParsingResult {

    /** 版面区域的边界框（[x1,y1,x2,y2]） */
    @JsonProperty("block_bbox")
    private int[] blockBbox;

    /** 版面区域标签（如"text"、"table"、"image"） */
    @JsonProperty("block_label")
    private String blockLabel;

    /** 版面区域内的文本内容（表格/公式为结构化标识） */
    @JsonProperty("block_content")
    private String blockContent;

    /** 是否为段落的开始标识 */
    @JsonProperty("seg_start_flag")
    private boolean segStartFlag;

    /** 是否为段落的结束标识 */
    @JsonProperty("seg_end_flag")
    private boolean segEndFlag;

    /** 版面区域的子标签（如"text"的子标签"title_text"） */
    @JsonProperty("sub_label")
    private String subLabel;

    /** 版面区域的子索引（用于恢复Markdown结构） */
    @JsonProperty("sub_index")
    private int subIndex;

    /** 版面区域的全局索引（用于排序） */
    @JsonProperty("index")
    private int index;


}