package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 表格识别结果：表格结构（HTML）和单元格内容
 */
@Data
public class TableResult {

    /**
     * 表格单元格的边界框列表
     */
    @JsonProperty("cell_box_list")
    private List<int[]> cellBoxList;

    /**
     * 表格结构的HTML字符串（可直接渲染）
     */
    @JsonProperty("pred_html")
    private String predHtml;

    /**
     * 表格单元格的OCR识别结果
     */
    @JsonProperty("table_ocr_pred")
    private TableOcrPred tableOcrPred;


}

