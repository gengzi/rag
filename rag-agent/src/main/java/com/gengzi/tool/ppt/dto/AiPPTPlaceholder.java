package com.gengzi.tool.ppt.dto;

import lombok.Data;

@Data
public class AiPPTPlaceholder {
    // 占位符唯一标识（如"title"）
    private String id;
    // 占位符名称（如"总标题"）
    private String name;
    // 占位符描述（如"首页顶部主标题"）
    private String description;
}
