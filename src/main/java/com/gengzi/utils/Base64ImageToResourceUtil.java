package com.gengzi.utils;

import cn.hutool.core.codec.Base64Decoder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class Base64ImageToResourceUtil {

    /**
     * 将 Base64 图片转换为 Spring Resource
     * @param base64Image 带前缀的 Base64 图片字符串（如 data:image/png;base64,xxxx）
     * @param resourceDescription 资源描述（建议传入图片名称+格式，如 "avatar.png"）
     * @return 转换后的 Resource 实例
     * @throws IllegalArgumentException 当 Base64 字符串为空或格式非法时抛出
     */
    public static Resource convert(String base64Image, String resourceDescription) {
        // 1. 校验 Base64 字符串非空
        if (!StringUtils.hasText(base64Image)) {
            throw new IllegalArgumentException("Base64 图片字符串不能为空");
        }

        // 2. 去除 Base64 前缀（如 data:image/png;base64,），提取纯编码部分
        String pureBase64;
        if (base64Image.startsWith("data:image/")) {
            pureBase64 = base64Image.split(",")[1]; // 分割前缀与编码内容
        } else {
            pureBase64 = base64Image; // 若已为纯 Base64，直接使用
        }

        // 3. Base64 解码为字节数组（Spring 工具类避免手动处理异常）
        byte[] imageBytes = Base64Decoder.decode(pureBase64);

        // 5. （可选）设置资源描述，便于识别（如日志打印时会显示）
        if (StringUtils.hasText(resourceDescription)) {
            return new ByteArrayResource(imageBytes,resourceDescription);
        }
        // 4. 包装为 ByteArrayResource（实现 Resource 接口）
        ByteArrayResource byteArrayResource = new ByteArrayResource(imageBytes);
        return byteArrayResource;
    }

    // 简化重载方法：无需手动传描述，默认用 "Base64-Image"
    public static Resource convert(String base64Image) {
        return convert(base64Image, "Base64-Image");
    }
}