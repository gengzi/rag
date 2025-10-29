package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 全局OCR结果：文本检测+文本识别的整合输出
 */
@Data
public class OverallOcrResult {

    /** OCR子产线配置（是否启用文本行方向分类） */
    @JsonProperty("model_settings")
    private OcrModelSettings modelSettings;

    /** 文本检测的多边形框列表（每个框为4个顶点坐标，shape: [n,4,2]） */
    @JsonProperty("dt_polys")
    private List<List<int[]>> dtPolys;

    /** 文本检测模块的配置参数（边长限制、阈值等） */
    @JsonProperty("text_det_params")
    private TextDetParams textDetParams;

    /** 文本类型（固定为"general"） */
    @JsonProperty("text_type")
    private String textType;

    /** 文本行方向分类结果（角度：0/180度，仅当 use_textline_orientation=true 时存在） */
    @JsonProperty("textline_orientation_angles")
    private List<Integer> textlineOrientationAngles;

    /** 文本识别结果过滤阈值（0~1） */
    @JsonProperty("text_rec_score_thresh")
    private double textRecScoreThresh;

    /** 文本识别结果列表（已按阈值过滤） */
    @JsonProperty("rec_texts")
    private List<String> recTexts;

    /** 文本识别置信度列表（与recTexts一一对应） */
    @JsonProperty("rec_scores")
    private List<Double> recScores;

    /** 过滤后的文本检测框列表（与recTexts一一对应） */
    @JsonProperty("rec_polys")
    private List<List<int[]>> recPolys;

    /** 文本检测框的矩形边界框数组（shape: [n,4]，每行为[x1,y1,x2,y2]） */
    @JsonProperty("rec_boxes")
    private List<int[]> recBoxes;




}

