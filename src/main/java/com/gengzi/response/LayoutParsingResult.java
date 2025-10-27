package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 版面解析请求成功后的结果体（对应响应体的result字段）
 */
@Data
public class LayoutParsingResult {

    /**
     * 版面解析结果数组
     * 说明：长度=1（图像输入）或实际处理页数（PDF输入），按页面顺序排列
     */
    @JsonProperty("layoutParsingResults")
    private List<LayoutParsingPageItem> layoutParsingResults;

    /**
     * 输入数据信息
     * 说明：包含输入文件的元数据（如文件名、格式等，具体字段由服务定义）
     */
    @JsonProperty("dataInfo")
    private Object dataInfo; // 服务未明确dataInfo的子字段，用Object兼容所有结构

}