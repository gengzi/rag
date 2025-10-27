package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OCR子产线的配置
 */
@Data
public class OcrModelSettings {
    @JsonProperty("use_doc_preprocessor")
    private boolean useDocPreprocessor; // 是否启用文档预处理
    @JsonProperty("use_textline_orientation")
    private boolean useTextlineOrientation; // 是否启用文本行方向分类
}
