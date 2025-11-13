package com.gengzi.tool.ppt.model;

import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ppt母版解析类
 *
 *  根据母版解析所有子版的内容信息
 *  首页： 标题，演讲者
 *  目录页 ：目录
 *  每一章：章节标题
 *  章节内容页：内容
 *  结束页：结束感谢
 */
@Data
public class PptMasterModel {
    // 母版名称
    private String masterName;
    // 所有子版式（Layout）
    private List<PptLayout> layouts = new ArrayList<>();
    // map形式快速查找
    private Map<String, PptLayout> layoutMap = new HashMap<>();
    // 母版页本身的元素（页脚、logo等）
    private List<PptMasterElement> masterElements = new ArrayList<>();

    // 快速查找版式
//    public PptLayout getLayoutByName(String layoutName) {
//        return layoutMap.get(layoutName);
//    }

    
    public PptLayout getLayoutByType(XSLFSlideLayoutType type) {
        return layouts.stream()
            .filter(layout -> layout.getLayoutType() == type)
            .findFirst()
            .orElse(null);
    }
}