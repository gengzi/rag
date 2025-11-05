package com.gengzi.tool.ppt.model;

import com.gengzi.tool.ppt.enums.PlaceholderContentType;
import lombok.Data;
import org.apache.poi.sl.usermodel.ShapeType;

import java.awt.geom.Rectangle2D;

@Data
public class PptPlaceholder {
    // 形状名称（来自 PowerPoint）
    private String shapeName;
    // 默认文本（如"标题"、"内容"） 如果是文本框才存在
    private String defaultText;
//    private PlaceholderContentType contentType;   // 内容类型
    private ShapeType shapeType;
    private Rectangle2D bounds;                   // 位置和大小
    private int zIndex;                           // 层级顺序
    private boolean isPlaceholder;                // 是否为真正的占位符（vs 普通文本框）
    private String placeholderId;                 // 占位符唯一标识（来自 PPTX 内部）

    /**
     * 可选文字：说明标题
     */
    private String descriptionTitle;
    /**
     * 可选文字：说明内容
     */
    private String description;




    // 生成唯一标识符（用于无法命名的情况）
    public String getEffectiveName() {
        if (shapeName != null && !shapeName.isEmpty() && !shapeName.startsWith("文本框")) {
            return shapeName;
        }
        // fallback: 使用类型 + 位置生成标识
        return shapeType.name() + "_" + zIndex;
    }
}