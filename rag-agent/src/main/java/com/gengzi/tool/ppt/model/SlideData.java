package com.gengzi.tool.ppt.model;

import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// 幻灯片数据模型（生成PPT时的内容数据）
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideData {
    // 唯一标识
    private String id;
    // 页面类型
    private XSLFSlideLayoutType type;
    // 页面占位符内容
    /**
     * 例如  title 标题：xxx趋势
     * 占位符key，对应的value
     */
//    private Map<String, PlaceholderData> data;
    private Map<String, String> data;
    // 扩展数据（如图片URL等）
    private Map<String, Object> extraData;


    /**
     * 排序字段
     */
    private int order;


}