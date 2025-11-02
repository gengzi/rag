package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 单个版面区域的检测信息（边界框、标签、置信度）
 */
@Data
public class LayoutBox {
    @JsonProperty("cls_id")
    private int clsId; // 类别ID（如2=文本、1=图像、0=段落标题）
    @JsonProperty("label")
    private String label; // 类别标签（如"text"、"image"、"paragraph_title"）
    @JsonProperty("score")
    private double score; // 检测置信度（0~1）
    @JsonProperty("coordinate")
    private double[] coordinate; // 边界框坐标：[x1, y1, x2, y2]（左上角、右下角）

}