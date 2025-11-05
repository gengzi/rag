package com.gengzi.tool.ppt.model;

import java.awt.geom.Rectangle2D;

public class PptMasterElement {
    private String elementId;
    private ElementType elementType;
    private Rectangle2D bounds;
    private String content; // 文本内容或资源路径

    public enum ElementType {
        LOGO, HEADER, FOOTER, BACKGROUND, DECORATION
    }
    
    // getters and setters...
}