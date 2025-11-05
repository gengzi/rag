package com.gengzi.tool.ppt.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 解析结果封装类
@Data
public class ParseResult {
    private boolean valid; // 格式是否有效
    private String errorMsg; // 错误信息（若无效）
    private String totalTitle; // 总标题
    private List<Chapter> chapters = new ArrayList<>(); // 章节列表
}