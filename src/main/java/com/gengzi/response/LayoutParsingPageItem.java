package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 单页版面解析结果（对应layoutParsingResults数组的元素）
 */
@Data
public class LayoutParsingPageItem {

    /**
     * 简化的预测结果
     * 说明：产线predict方法res字段的简化版，去除input_path和page_index字段
     */
    @JsonProperty("prunedResult")
    private PrunedResult prunedResult; // 服务未明确prunedResult子字段，用Object兼容

    /**
     * Markdown结果
     * 说明：包含当前页的Markdown文本和图片信息
     */
    @JsonProperty("markdown")
    private LayoutMarkdownResult markdown;

    /**
     * 输出图像（Base64编码）
     * 说明：key=图像名称（如"visualize_img"），value=JPEG格式的Base64字符串；未返回时为null
     */
    @JsonProperty("outputImages")
    private Map<String, String> outputImages;

    /**
     * 输入图像（Base64编码）
     * 说明：当前页的原始输入图像（JPEG格式）；未返回时为null
     */
    @JsonProperty("inputImage")
    private String inputImage;

}