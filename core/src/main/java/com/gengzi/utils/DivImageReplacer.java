package com.gengzi.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 替换包含特定图片路径的<div>标签为固定文本
 */
public class DivImageReplacer {
    
    /**
     * 替换文本中包含指定图片路径的整个div标签
     * 
     * @param inputText 输入文本
     * @param imageFileName 要匹配的图片文件名
     * @param replacementText 替换后的固定文本
     * @return 替换后的文本
     */
    public static String replaceDivWithImage(String inputText, String imageFileName, String replacementText) {
        if (inputText == null || imageFileName == null || replacementText == null) {
            throw new IllegalArgumentException("输入参数不能为null");
        }
        
        // 转义图片文件名中的特殊字符，确保正则匹配正确
        String escapedImageFileName = Pattern.quote(imageFileName);
        
        // 构建正则表达式
        // 匹配<div ...>...</div>，其中包含指定的图片路径
        String regex = "<div[^>]*?>.*?<img[^>]*?src\\s*=\\s*[\"'].*?" + escapedImageFileName + "[\"'].*?>.*?</div>";
        
        // 编译正则表达式，使用DOTALL模式让.匹配包括换行符在内的所有字符
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        
        // 创建匹配器并替换所有匹配项
        Matcher matcher = pattern.matcher(inputText);
        return matcher.replaceAll(replacementText);
    }
    
    // 示例用法
    public static void main(String[] args) {
        // 示例输入文本
        String input = "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_81_0_1081_551.jpg\" alt=\"Image\" width=\"84%\" /></div>";
        
        // 要匹配的图片文件名
        String imageFileName = "img_in_image_box_81_0_1081_551.jpg";
        
        // 替换后的固定文本
        String replacement = "这是替换后的固定文本";
        
        // 执行替换
        String result = replaceDivWithImage(input, imageFileName, replacement);
        
        // 输出结果
        System.out.println("替换前: " + input);
        System.out.println("替换后: " + result);
    }
}
