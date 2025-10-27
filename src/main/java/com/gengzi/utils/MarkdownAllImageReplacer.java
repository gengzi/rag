package com.gengzi.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownAllImageReplacer {

    /**
     * 替换 Markdown 中所有图片（无论路径类型）为指定文本
     * @param mdContent Markdown 内容（含任意类型图片）
     * @param replacement 替换后的文本（如"[图片]"）
     * @return 替换后的 Markdown 内容
     */
    public static String replaceAllImages(String mdContent, String replacement) {
        if (mdContent == null || mdContent.isEmpty()) {
            return mdContent;
        }

        // 正则表达式：匹配所有 Markdown 图片语法
        // 格式：![alt](path) 或 ![](path)（alt 可选）
        // 路径支持：URL、本地路径、Base64 编码（含特殊字符）
        String regex = "!\\[([^]]*)\\]\\([^)]+\\)";
        // 正则说明：
        // !\\[([^]]*)\\] ：匹配 ![alt] 部分，其中 [^]]* 表示除 ] 外的任意字符（alt 可选，可为空）
        // \\([^)]+\\) ：匹配 (path) 部分，其中 [^)]+ 表示除 ) 外的任意字符（覆盖所有路径类型）

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mdContent);

        // 替换所有匹配到的图片标签
        return matcher.replaceAll(replacement);
    }

    // 测试示例
    public static void main(String[] args) {
        // 包含多种图片类型的 Markdown 内容
        String md = "1. 网络图片：![网络](https://example.com/img.png)\n" +
                    "2. 本地图片：![本地](./images/file.jpg)\n" +
                    "3. Base64图片：![base64](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...)\n" +
                    "4. 无alt图片：![](empty.png)";

        // 统一替换为 "[图片]"
        String result = replaceAllImages(md, "[图片]");

        System.out.println("替换后：");
        System.out.println(result);
        /* 输出结果：
        1. 网络图片：[图片]
        2. 本地图片：[图片]
        3. Base64图片：[图片]
        4. 无alt图片：[图片]
        */
    }
}