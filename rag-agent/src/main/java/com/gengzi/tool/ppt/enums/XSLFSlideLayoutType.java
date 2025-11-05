package com.gengzi.tool.ppt.enums;

public enum XSLFSlideLayoutType {
    HOME_PAGE("首页", "home_page"),
    CATALOGUE_PAGE("目录页", "catalogue_page"),
    TEXT_CONTENT_PAGE("文本内容页", "text_content_page"),
    ENDING_PAGE("结尾页", "ending_page");;


    private String pageName;
    private String pageType;
    XSLFSlideLayoutType(String pageName, String pageType) {
        this.pageName = pageName;
        this.pageType = pageType;
    }

    // 通过 type 获取对应的枚举（大小写敏感）
    public static XSLFSlideLayoutType fromType(String type) {
        for (XSLFSlideLayoutType pageType : XSLFSlideLayoutType.values()) {
            if (pageType.pageType.equals(type)) {
                return pageType;
            }
        }
        throw new IllegalArgumentException("No PageType with type: " + type);
    }

    // 可选：提供一个安全的版本，返回 null 而不是抛异常
    public static XSLFSlideLayoutType fromTypeOrNull(String type) {
        for (XSLFSlideLayoutType pageType : XSLFSlideLayoutType.values()) {
            if (pageType.pageType.equals(type)) {
                return pageType;
            }
        }
        return null;
    }

    // 可选：忽略大小写的版本（根据需求决定是否需要）
    public static XSLFSlideLayoutType fromTypeIgnoreCase(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Type must not be null");
        }
        for (XSLFSlideLayoutType pageType : XSLFSlideLayoutType.values()) {
            if (pageType.pageType.equalsIgnoreCase(type)) {
                return pageType;
            }
        }
        throw new IllegalArgumentException("No PageType with type (ignoring case): " + type);
    }

    public String getPageName() {
        return pageName;
    }

    public String getPageType() {
        return pageType;
    }


}
