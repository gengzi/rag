package com.gengzi.tool.ppt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 内部类：存储小结信息
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Section {
    private int chapterNum; // 所属章节编号
    private int sectionNum; // 小结编号
    private String sectionTitle; // 小结标题（如"小结1.1"）

}