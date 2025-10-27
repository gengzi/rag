package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 印章检测模块的配置参数（与文本检测略有差异）
 */
@Data
public class SealDetParams {
    @JsonProperty("limit_side_len")
    private int limitSideLen; // 默认736
    @JsonProperty("limit_type")
    private String limitType; // 默认"min"
    @JsonProperty("thresh")
    private double thresh; // 默认0.2
    @JsonProperty("box_thresh")
    private double boxThresh; // 默认0.6
    @JsonProperty("unclip_ratio")
    private double unclipRatio; // 默认0.5

}