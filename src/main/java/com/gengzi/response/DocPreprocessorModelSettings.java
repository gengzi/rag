package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 文档预处理子模块的配置
 */
@Data
public class DocPreprocessorModelSettings {
    @JsonProperty("use_doc_orientation_classify")
    private boolean useDocOrientationClassify; // 是否启用文档方向分类
    @JsonProperty("use_doc_unwarping")
    private boolean useDocUnwarping; // 是否启用文本图像矫正

}
