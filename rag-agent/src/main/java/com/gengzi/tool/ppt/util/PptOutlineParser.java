package com.gengzi.tool.ppt.util;

import com.gengzi.tool.ppt.dto.Chapter;
import com.gengzi.tool.ppt.dto.ParseResult;
import com.gengzi.tool.ppt.dto.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PPT大纲格式验证与信息提取工具类
 * 支持格式：
 * # 总标题
 * ## 章节1：章节标题内容
 * ### 小结1.1：小结标题内容
 * ### 小结1.2：小结标题内容
 * ## 章节2：章节标题内容
 * ### 小结2.1：小结标题内容
 * ...
 */
public class PptOutlineParser {

    // 正则表达式模式定义（修正匹配规则，支持标题内容提取）
    // 总标题：# 后接标题内容（允许空格和特殊字符）
    private static final Pattern TITLE_PATTERN = Pattern.compile("^# (.*)$");
    // 章节：## 章节X：后接章节标题内容（捕获章节号和标题）
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^## 章节(\\d+)：(.*)$");
    // 小结：### 小结X.Y：后接小结标题内容（捕获章节号、小结号和标题）
    private static final Pattern SECTION_PATTERN = Pattern.compile("^### 小结(\\d+)\\.(\\d+)：(.*)$");

    /**
     * 验证PPT大纲格式并提取信息
     *
     * @param outlineContent PPT大纲文本（按行分隔）
     * @return 解析结果（包含验证状态和提取的信息）
     */
    public static ParseResult validateAndExtract(String outlineContent) {
        ParseResult result = new ParseResult();
        // 拆分行并过滤空行（连续空行也会被过滤）
        List<String> lines = new ArrayList<>();
        for (String line : outlineContent.split("\n")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                lines.add(trimmedLine);
            }
        }

        // 若没有任何有效行，直接返回错误
        if (lines.isEmpty()) {
            result.setValid(false);
            result.setErrorMsg("大纲内容为空");
            return result;
        }

        // 状态变量
        String totalTitle = null;
        int currentChapterNum = 0; // 当前章节编号（用于校验连续性）
        Chapter currentChapter = null;
        int currentSectionNum = 0; // 当前章节下的小结编号（用于校验连续性）

        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            String line = lines.get(lineIdx);
            // 行号（原始内容的行号，用于错误提示，+1是因为索引从0开始）
            int lineNum = lineIdx + 1;

            // 匹配总标题（必须是第一行有效行）
            if (lineIdx == 0) {
                Matcher titleMatcher = TITLE_PATTERN.matcher(line);
                if (!titleMatcher.matches()) {
                    result.setValid(false);
                    result.setErrorMsg("第" + lineNum + "行：必须是总标题（格式：# 标题内容）");
                    return result;
                }
                totalTitle = titleMatcher.group(1).trim();
                result.setTotalTitle(totalTitle);
                continue;
            }

            // 非第一行：匹配章节或小结
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            Matcher sectionMatcher = SECTION_PATTERN.matcher(line);

            if (chapterMatcher.matches()) {
                // 处理章节
                int chapterNum = Integer.parseInt(chapterMatcher.group(1));
                String chapterTitle = chapterMatcher.group(2).trim(); // 提取章节标题内容

                // 校验章节编号连续性（必须从1开始，依次递增）
                if (chapterNum != currentChapterNum + 1) {
                    result.setValid(false);
                    result.setErrorMsg("第" + lineNum + "行：章节编号不连续（应为章节" + (currentChapterNum + 1) + "）");
                    return result;
                }
                // 校验章节标题不为空
                if (chapterTitle.isEmpty()) {
                    result.setValid(false);
                    result.setErrorMsg("第" + lineNum + "行：章节标题不能为空（格式：## 章节X：标题内容）");
                    return result;
                }

                // 创建新章节（存储实际标题内容）
                currentChapter = new Chapter(chapterNum, chapterTitle);
                result.getChapters().add(currentChapter);
                currentChapterNum = chapterNum;
                currentSectionNum = 0; // 重置当前章节的小结编号计数

            } else if (sectionMatcher.matches()) {
                // 处理小结
                if (currentChapter == null) {
                    result.setValid(false);
                    result.setErrorMsg("第" + lineNum + "行：小结必须包含在章节内（需先定义章节）");
                    return result;
                }

                int sectionChapterNum = Integer.parseInt(sectionMatcher.group(1));
                int sectionNum = Integer.parseInt(sectionMatcher.group(2));
                String sectionTitle = sectionMatcher.group(3).trim(); // 提取小结标题内容

                // 校验小结所属章节是否正确
                if (sectionChapterNum != currentChapterNum) {
                    result.setValid(false);
                    result.setErrorMsg("第" + lineNum + "行：小结所属章节错误（当前章节为" + currentChapterNum + "，应为小结" + currentChapterNum + ".X）");
                    return result;
                }
                // 校验小结编号连续性（必须从1开始，依次递增）
                if (sectionNum != currentSectionNum + 1) {
                    result.setValid(false);
                    result.setErrorMsg("第" + lineNum + "行：小结编号不连续（应为小结" + currentChapterNum + "." + (currentSectionNum + 1) + "）");
                    return result;
                }
                // 校验小结标题不为空
                if (sectionTitle.isEmpty()) {
                    result.setValid(false);
                    result.setErrorMsg("第" + lineNum + "行：小结标题不能为空（格式：### 小结X.Y：标题内容）");
                    return result;
                }

                // 添加小结到当前章节（存储实际标题内容）
                currentChapter.getSections().add(new Section(sectionChapterNum, sectionNum, sectionTitle));
                currentSectionNum = sectionNum;

            } else {
                // 既不是章节也不是小结，格式错误
                result.setValid(false);
                result.setErrorMsg("第" + lineNum + "行：格式错误（应为## 章节X：标题内容 或 ### 小结X.Y：标题内容）");
                return result;
            }
        }

        // 额外校验：必须至少有一个章节，每个章节至少有一个小结
        if (result.getChapters().isEmpty()) {
            result.setValid(false);
            result.setErrorMsg("缺少章节（至少需要一个章节，格式：## 章节1：标题内容）");
            return result;
        }
        for (Chapter chapter : result.getChapters()) {
            if (chapter.getSections().isEmpty()) {
                result.setValid(false);
                result.setErrorMsg("章节" + chapter.getChapterNum() + "：缺少小结（每个章节至少需要一个小结，格式：### 小结" + chapter.getChapterNum() + ".1：标题内容）");
                return result;
            }
        }

        // 所有校验通过
        result.setValid(true);
        return result;
    }

    // 测试方法
    public static void main(String[] args) {
        // 测试用例：正确格式（包含空行、章节/小结标题内容）
        String validOutline = "# 黄金价格波动分析与趋势预测\n" +
                "\n" + // 空行（会被过滤）
                "## 章节1：黄金价格的核心影响因素\n" +
                "### 小结1.1：供求关系对价格的基础作用\n" +
                "### 小结1.2：美元汇率与黄金价格的联动关系\n" +
                "\n\n" + // 连续空行（会被过滤）
                "## 章节2：近五年黄金价格走势回顾\n" +
                "### 小结2.1：2019-2021年价格波动特征\n" +
                "### 小结2.2：2022-2024年关键转折点分析\n" +
                "### 小结2.3：走势背后的驱动因素解析";

        // 测试用例1：验证正确格式
        ParseResult validResult = validateAndExtract(validOutline);
        System.out.println("正确格式验证结果：" + (validResult.isValid() ? "通过" : "失败"));
        if (validResult.isValid()) {
            System.out.println("总标题：" + validResult.getTotalTitle());
            for (Chapter chapter : validResult.getChapters()) {
                System.out.println("章节" + chapter.getChapterNum() + "：" + chapter.getChapterTitle());
                for (Section section : chapter.getSections()) {
                    System.out.println("  小结" + section.getChapterNum() + "." + section.getSectionNum() + "：" + section.getSectionTitle());
                }
            }
        }

        // 测试用例2：错误格式（章节标题为空）
        String invalidOutline1 = "# 黄金价格分析\n" +
                "## 章节1：\n" + // 错误：章节标题为空
                "### 小结1.1：xxx";
        ParseResult invalidResult1 = validateAndExtract(invalidOutline1);
        System.out.println("\n错误用例1验证结果：" + (invalidResult1.isValid() ? "通过" : "失败"));
        System.out.println("错误信息：" + invalidResult1.getErrorMsg());

        // 测试用例3：错误格式（小结编号不连续）
        String invalidOutline2 = "# 黄金价格分析\n" +
                "## 章节1：xxx\n" +
                "### 小结1.1：xxx\n" +
                "### 小结1.3：xxx"; // 错误：应为小结1.2
        ParseResult invalidResult2 = validateAndExtract(invalidOutline2);
        System.out.println("\n错误用例2验证结果：" + (invalidResult2.isValid() ? "通过" : "失败"));
        System.out.println("错误信息：" + invalidResult2.getErrorMsg());
    }
}