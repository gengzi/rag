package com.gengzi.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 版面解析接口请求体
 * 对应 POST /layout-parsing 的请求参数结构
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // 序列化时忽略null值，减少请求体体积
@Data
public class LayoutParsingRequest {

    /**
     * 服务器可访问的文件URL或文件Base64编码
     * 必选：是
     * 说明：支持图像文件或PDF文件，默认处理PDF前10页（解除限制需配置max_num_input_imgs: null）
     */
    @JsonProperty("file")
    private String file;

    /**
     * 文件类型
     * 必选：否
     * 说明：0=PDF文件，1=图像文件；未传则根据URL推断
     */
    @JsonProperty("fileType")
    private Integer fileType;

    /**
     * 文档方向分类开关
     * 必选：否
     * 说明：参考产线predict方法的use_doc_orientation_classify参数
     */
    @JsonProperty("useDocOrientationClassify")
    private Boolean useDocOrientationClassify;

    /**
     * 文档展平开关
     * 必选：否
     * 说明：参考产线predict方法的use_doc_unwarping参数
     */
    @JsonProperty("useDocUnwarping")
    private Boolean useDocUnwarping;

    /**
     * 文本行方向分类开关
     * 必选：否
     * 说明：参考产线predict方法的use_textline_orientation参数
     */
    @JsonProperty("useTextlineOrientation")
    private Boolean useTextlineOrientation;

    /**
     * 印章识别开关
     * 必选：否
     * 说明：参考产线predict方法的use_seal_recognition参数
     */
    @JsonProperty("useSealRecognition")
    private Boolean useSealRecognition;

    /**
     * 表格识别开关
     * 必选：否
     * 说明：参考产线predict方法的use_table_recognition参数
     */
    @JsonProperty("useTableRecognition")
    private Boolean useTableRecognition;

    /**
     * 公式识别开关
     * 必选：否
     * 说明：参考产线predict方法的use_formula_recognition参数
     */
    @JsonProperty("useFormulaRecognition")
    private Boolean useFormulaRecognition;

    /**
     * 图表识别开关
     * 必选：否
     * 说明：参考产线predict方法的use_chart_recognition参数
     */
    @JsonProperty("useChartRecognition")
    private Boolean useChartRecognition;

    /**
     * 区域检测开关
     * 必选：否
     * 说明：参考产线predict方法的use_region_detection参数
     */
    @JsonProperty("useRegionDetection")
    private Boolean useRegionDetection;

    /**
     * 版面检测阈值
     * 必选：否
     * 说明：参考产线predict方法的layout_threshold参数，支持number/object类型
     */
    @JsonProperty("layoutThreshold")
    private Object layoutThreshold;

    /**
     * 版面NMS（非极大值抑制）开关
     * 必选：否
     * 说明：参考产线predict方法的layout_nms参数
     */
    @JsonProperty("layoutNms")
    private Boolean layoutNms;

    /**
     * 版面解裁剪比例
     * 必选：否
     * 说明：参考产线predict方法的layout_unclip_ratio参数，支持number/array/object类型
     */
    @JsonProperty("layoutUnclipRatio")
    private Object layoutUnclipRatio;

    /**
     * 版面合并边界框模式
     * 必选：否
     * 说明：参考产线predict方法的layout_merge_bboxes_mode参数，支持string/object类型
     */
    @JsonProperty("layoutMergeBboxesMode")
    private Object layoutMergeBboxesMode;

    /**
     * 文本检测限制边长
     * 必选：否
     * 说明：参考产线predict方法的text_det_limit_side_len参数
     */
    @JsonProperty("textDetLimitSideLen")
    private Integer textDetLimitSideLen;

    /**
     * 文本检测限制类型
     * 必选：否
     * 说明：参考产线predict方法的text_det_limit_type参数
     */
    @JsonProperty("textDetLimitType")
    private String textDetLimitType;

    /**
     * 文本检测阈值
     * 必选：否
     * 说明：参考产线predict方法的text_det_thresh参数
     */
    @JsonProperty("textDetThresh")
    private Double textDetThresh;

    /**
     * 文本检测边界框阈值
     * 必选：否
     * 说明：参考产线predict方法的text_det_box_thresh参数
     */
    @JsonProperty("textDetBoxThresh")
    private Double textDetBoxThresh;

    /**
     * 文本检测解裁剪比例
     * 必选：否
     * 说明：参考产线predict方法的text_det_unclip_ratio参数
     */
    @JsonProperty("textDetUnclipRatio")
    private Double textDetUnclipRatio;

    /**
     * 文本识别分数阈值
     * 必选：否
     * 说明：参考产线predict方法的text_rec_score_thresh参数
     */
    @JsonProperty("textRecScoreThresh")
    private Double textRecScoreThresh;

    /**
     * 印章检测限制边长
     * 必选：否
     * 说明：参考产线predict方法的seal_det_limit_side_len参数
     */
    @JsonProperty("sealDetLimitSideLen")
    private Integer sealDetLimitSideLen;

    /**
     * 印章检测限制类型
     * 必选：否
     * 说明：参考产线predict方法的seal_det_limit_type参数
     */
    @JsonProperty("sealDetLimitType")
    private String sealDetLimitType;

    /**
     * 印章检测阈值
     * 必选：否
     * 说明：参考产线predict方法的seal_det_thresh参数
     */
    @JsonProperty("sealDetThresh")
    private Double sealDetThresh;

    /**
     * 印章检测边界框阈值
     * 必选：否
     * 说明：参考产线predict方法的seal_det_box_thresh参数
     */
    @JsonProperty("sealDetBoxThresh")
    private Double sealDetBoxThresh;

    /**
     * 印章检测解裁剪比例
     * 必选：否
     * 说明：参考产线predict方法的seal_det_unclip_ratio参数
     */
    @JsonProperty("sealDetUnclipRatio")
    private Double sealDetUnclipRatio;

    /**
     * 印章识别分数阈值
     * 必选：否
     * 说明：参考产线predict方法的seal_rec_score_thresh参数
     */
    @JsonProperty("sealRecScoreThresh")
    private Double sealRecScoreThresh;

    /**
     * 有线表格单元格转HTML开关
     * 必选：否
     * 说明：参考产线predict方法的use_wired_table_cells_trans_to_html参数
     */
    @JsonProperty("useWiredTableCellsTransToHtml")
    private Boolean useWiredTableCellsTransToHtml;

    /**
     * 无线表格单元格转HTML开关
     * 必选：否
     * 说明：参考产线predict方法的use_wireless_table_cells_trans_to_html参数
     */
    @JsonProperty("useWirelessTableCellsTransToHtml")
    private Boolean useWirelessTableCellsTransToHtml;

    /**
     * 表格方向分类开关
     * 必选：否
     * 说明：参考产线predict方法的use_table_orientation_classify参数
     */
    @JsonProperty("useTableOrientationClassify")
    private Boolean useTableOrientationClassify;

    /**
     * OCR结果与表格单元格关联开关
     * 必选：否
     * 说明：参考产线predict方法的use_ocr_results_with_table_cells参数
     */
    @JsonProperty("useOcrResultsWithTableCells")
    private Boolean useOcrResultsWithTableCells;

    /**
     * 端到端有线表格识别模型开关
     * 必选：否
     * 说明：参考产线predict方法的use_e2e_wired_table_rec_model参数
     */
    @JsonProperty("useE2eWiredTableRecModel")
    private Boolean useE2eWiredTableRecModel;

    /**
     * 端到端无线表格识别模型开关
     * 必选：否
     * 说明：参考产线predict方法的use_e2e_wireless_table_rec_model参数
     */
    @JsonProperty("useE2eWirelessTableRecModel")
    private Boolean useE2eWirelessTableRecModel;

    /**
     * 可视化结果返回开关
     * 必选：否
     * 说明：true=返回图像，false=不返回；未传则遵循产线Serving.visualize配置（默认返回）
     */
    @JsonProperty("visualize")
    private Boolean visualize;

}