package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 文本检测模块的配置参数
 */
@Data
public class TextDetParams {
    @JsonProperty("limit_side_len")
    private int limitSideLen; // 图像边长限制（默认960）
    @JsonProperty("limit_type")
    private String limitType; // 限制类型（"min"或"max"）
    @JsonProperty("thresh")
    private double thresh; // 像素检测阈值（默认0.3）
    @JsonProperty("box_thresh")
    private double boxThresh; // 检测框阈值（默认0.6）
    @JsonProperty("unclip_ratio")
    private double unclipRatio; // 文本区域扩张系数（默认2.0）

}