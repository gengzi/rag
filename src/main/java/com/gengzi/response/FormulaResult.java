package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 公式识别结果：单个公式的检测与识别信息
 */
@Data
public class FormulaResult {

    @JsonProperty("rec_formula")
    private String recFormula; // 公式识别结果（LaTeX源码）
    @JsonProperty("rec_polys")
    private int[] recPolys; // 公式检测框坐标：[x1,y1,x2,y2,x3,y3,x4,y4]（4个顶点）
    @JsonProperty("formula_region_id")
    private int formulaRegionId; // 公式所在区域编号

}