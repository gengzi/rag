package com.gengzi.tool.ppt.model;

import com.gengzi.tool.ppt.enums.PlaceholderContentType;
import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
import lombok.Data;
import org.apache.poi.sl.usermodel.ShapeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PptLayout {
    private String name;                          // 版式名称（如"标题幻灯片"）
    private XSLFSlideLayoutType layoutType;           // 版式类型
    private int layoutIndex;                      // 在母版中的索引
    private List<PptPlaceholder> placeholders = new ArrayList<>();    // 该版式中的占位符列表
    private Map<String, PptPlaceholder> placeholderMap = new HashMap<>(); // 按名称/类型快速查找

    // 快速查找占位符
    public PptPlaceholder getPlaceholderByName(String name) {
        return placeholderMap.get(name);
    }

    public PptPlaceholder getPlaceholderByType(ShapeType type) {
        return placeholders.stream()
                .filter(ph -> ph.getShapeType() == type)
                .findFirst()
                .orElse(null);
    }
}