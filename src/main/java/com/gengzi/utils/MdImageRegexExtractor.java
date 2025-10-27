package com.gengzi.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MdImageRegexExtractor {

    // 正则1：匹配标准 Markdown 图片语法 ![alt](url)
    // 分组说明：group(2) 提取图片 URL（支持带标题的情况，如 ![](url "title")）
    private static final Pattern MD_IMAGE_PATTERN = Pattern.compile(
        "!\\[([^]]*)\\]\\(([^)]+)\\)"
    );

    // 正则2：匹配 HTML <img> 标签中的 src 属性
    // 分组说明：group(1) 提取 src 的值（支持单引号、双引号或无引号）
    private static final Pattern HTML_IMG_PATTERN = Pattern.compile(
        "<img\\s+[^>]*src\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^'\"\\s>]+))[^>]*>"
    );

    /**
     * 从 Markdown 文本中提取所有图片资源（正则方式）
     * @param mdContent Markdown 文本内容
     * @return 去重后的图片 URL/路径列表
     */
    public static List<String> extractImages(String mdContent) {
        if (mdContent == null || mdContent.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> imageUrls = new HashSet<>(); // 去重

        // 1. 提取标准 Markdown 图片（![alt](url)）
        Matcher mdMatcher = MD_IMAGE_PATTERN.matcher(mdContent);
        while (mdMatcher.find()) {
            String url = mdMatcher.group(2).trim();
            // 处理带标题的情况（如 url "title" → 只保留 url）
            if (url.contains(" ")) {
                url = url.split("\\s+")[0]; // 取第一个空格前的内容
            }
            imageUrls.add(url);
        }

        // 2. 提取 HTML <img> 标签中的 src
        Matcher htmlMatcher = HTML_IMG_PATTERN.matcher(mdContent);
        while (htmlMatcher.find()) {
            // 从分组中取非空的 src 值（支持单引号、双引号、无引号三种情况）
            String src = null;
            for (int i = 2; i <= 4; i++) {
                if (htmlMatcher.group(i) != null) {
                    src = htmlMatcher.group(i).trim();
                    break;
                }
            }
            if (src != null && !src.isEmpty()) {
                imageUrls.add(src);
            }
        }

        return new ArrayList<>(imageUrls);
    }

    // 测试示例
    public static void main(String[] args) {
        String mdContent = """
                # 正则提取图片测试

                ## 标准 Markdown 图片
                ![网络图](https://example.com/img1.png)
                ![本地图](./images/img2.jpg "示例图片")  // 带标题
                ![Base64图](data:image/png;base64,xxxx)

                ## HTML 图片标签
                <img src='/static/img3.gif' alt='测试'>  // 单引号
                <img src=/icons/img4.svg>  // 无引号
                <div><img src="img5.png" width="100"></div>  // 双引号
                """;

        List<String> images = extractImages(mdContent);
        System.out.println("提取到的图片资源：");
        for (String img : images) {
            System.out.println("- " + img);
        }
    }
}