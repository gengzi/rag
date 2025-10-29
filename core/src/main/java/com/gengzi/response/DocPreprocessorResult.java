package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 文档预处理结果：方向分类、图像矫正
 */
@Data
public class DocPreprocessorResult {

    /**
     * 预处理子产线的模型配置（是否启用方向分类、图像矫正）
     */
    @JsonProperty("model_settings")
    private DocPreprocessorModelSettings modelSettings;

    /**
     * 文档方向分类结果（角度：0/90/180/270度，仅当 use_doc_orientation_classify=true 时存在）
     */
    @JsonProperty("angle")
    private int angle;


}
