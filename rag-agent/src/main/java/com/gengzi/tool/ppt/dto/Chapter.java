package com.gengzi.tool.ppt.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 内部类：存储章节信息
@Data
public class Chapter {
    private int chapterNum; // 章节编号
    private String chapterTitle; // 章节标题（如"章节1"）
    private List<Section> sections = new ArrayList<>(); // 下属小结列表

    // 构造器和getter
    public Chapter(int chapterNum, String chapterTitle) {
        this.chapterNum = chapterNum;
        this.chapterTitle = chapterTitle;
    }

}