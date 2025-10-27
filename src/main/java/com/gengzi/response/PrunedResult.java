package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * PP-StructureV3 产线输出中 prunedResult 字段的顶层实体类
 * （去除了原始 res 字段中的 input_path 和 page_index）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略JSON中未定义的字段，提高兼容性
public class PrunedResult {

    /**
     * 产线模型配置（是否启用印章识别、表格识别等）
     */
    @JsonProperty("model_settings")
    private ModelSettings modelSettings;

    /**
     * 文档预处理结果（仅当 use_doc_preprocessor=true 时存在）
     * 包含文档方向分类、文本图像矫正结果
     */
    @JsonProperty("doc_preprocessor_res")
    private DocPreprocessorResult docPreprocessorRes;

    /**
     * 版面区域检测结果
     * 包含检测到的文本块、表格、图像等区域的边界框、标签、置信度
     */
    @JsonProperty("layout_det_res")
    private LayoutDetectionResult layoutDetRes;

    /**
     * 全局OCR识别结果
     * 包含文本检测框、识别文本内容、置信度等
     */
    @JsonProperty("overall_ocr_res")
    private OverallOcrResult overallOcrRes;

    /**
     * 公式识别结果列表（仅当 use_formula_recognition=true 时存在）
     */
    @JsonProperty("formula_res_list")
    private List<FormulaResult> formulaResList;

    /**
     * 印章文本识别结果（仅当 use_seal_recognition=true 时存在）
     */
    @JsonProperty("seal_res_list")
    private List<SealResult> sealResList;

    /**
     * 表格识别结果列表（仅当 use_table_recognition=true 时存在）
     */
    @JsonProperty("table_res_list")
    private List<TableResult> tableResList;

    /**
     * 解析结果列表（按阅读顺序排列的版面区域内容）
     */
    @JsonProperty("parsing_res_list")
    private List<ParsingResult> parsingResList;


}