package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 版面区域检测结果：识别文档中的文本、表格、图像等区域
 */
@Data
public class LayoutDetectionResult {

    /** 检测到的版面区域列表 */
    @JsonProperty("boxes")
    private List<LayoutBox> boxes;


}

