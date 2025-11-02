package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 印章文本识别结果：印章区域的检测与文本识别
 */
@Data
public class SealResult {

    /**
     * 印章检测的多边形框列表（同OCR的dt_polys）
     */
    @JsonProperty("dt_polys")
    private List<List<int[]>> dtPolys;

    /**
     * 印章检测模块的配置参数（同TextDetParams）
     */
    @JsonProperty("text_det_params")
    private SealDetParams textDetParams;

    /**
     * 印章检测类型（固定为"seal"）
     */
    @JsonProperty("text_type")
    private String textType;

    /**
     * 印章文本识别阈值
     */
    @JsonProperty("seal_rec_score_thresh")
    private double sealRecScoreThresh;

    /**
     * 印章文本识别结果列表
     */
    @JsonProperty("rec_texts")
    private List<String> recTexts;

    /**
     * 印章文本识别置信度列表
     */
    @JsonProperty("rec_scores")
    private List<Double> recScores;

    /**
     * 印章检测框的矩形边界框数组
     */
    @JsonProperty("rec_boxes")
    private List<int[]> recBoxes;


}

