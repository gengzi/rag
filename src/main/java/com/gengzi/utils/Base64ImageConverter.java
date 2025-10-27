package com.gengzi.utils;

import java.util.Base64;

public class Base64ImageConverter {

    /**
     * 将 Base64 编码的图片字符串转换为字节数组
     * @param base64Image Base64 编码的图片字符串（可包含或不包含前缀）
     * @return 图片的字节数组
     */
    public static byte[] base64ToBytes(String base64Image) {
        // 处理可能的前缀（如 "data:image/png;base64,"）
        String base64Data = base64Image;
        if (base64Image.contains(",")) {
            // 截取逗号后的纯 Base64 数据部分
            base64Data = base64Image.split(",")[1];
        }

        // 解码为字节数组
        return Base64.getDecoder().decode(base64Data);
    }

    // 示例用法
    public static void main(String[] args) {
        // 示例：Base64 编码的图片字符串（实际使用中替换为真实的 Base64 数据）
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+P+/HgAFeAJ5gMmAAAAABJRU5ErkJggg==";
        
        try {
            byte[] imageBytes = base64ToBytes(base64Image);
            System.out.println("转换成功，字节数组长度：" + imageBytes.length + " 字节");
            // 此时 imageBytes 可用于：写入文件、上传到服务器等操作
        } catch (IllegalArgumentException e) {
            System.err.println("Base64 解码失败：" + e.getMessage());
        }
    }
}