package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 表格单元格的OCR识别结果
 */
@Data
public class TableOcrPred {
    @JsonProperty("rec_polys")
    private List<List<int[]>> recPolys; // 单元格检测框
    @JsonProperty("rec_texts")
    private List<String> recTexts; // 单元格文本内容
    @JsonProperty("rec_scores")
    private List<Double> recScores; // 单元格识别置信度
    @JsonProperty("rec_boxes")
    private List<int[]> recBoxes; // 单元格矩形边界框

}