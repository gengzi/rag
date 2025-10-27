package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 产线模型配置：标识是否启用各功能模块
 */
@Data
public class ModelSettings {

    /** 是否启用文档预处理子产线 */
    @JsonProperty("use_doc_preprocessor")
    private boolean useDocPreprocessor;

    /** 是否启用印章文本识别子产线 */
    @JsonProperty("use_seal_recognition")
    private boolean useSealRecognition;

    /** 是否启用表格识别子产线 */
    @JsonProperty("use_table_recognition")
    private boolean useTableRecognition;

    /** 是否启用公式识别子产线 */
    @JsonProperty("use_formula_recognition")
    private boolean useFormulaRecognition;

}