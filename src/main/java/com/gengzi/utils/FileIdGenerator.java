package com.gengzi.utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileIdGenerator {

    /**
     * 生成文件唯一ID，基于文件路径的SHA-256哈希
     * @param filePath 文件路径
     * @return 格式为"file_哈希值"的唯一ID
     */
    public static String generateFileId(String filePath) {
        try {
            // 创建SHA-256消息摘要实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // 计算哈希值
            byte[] hashBytes = digest.digest(filePath.getBytes(StandardCharsets.UTF_8));
            
            // 将字节数组转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // 添加前缀并返回
            return "f_" + hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            // SHA-256是Java标准算法，通常不会抛出此异常
            throw new RuntimeException("SHA-256 algorithm not found", e);
        } catch (Exception e) {
            throw new RuntimeException("Error generating file ID", e);
        }
    }

    /**
     * 生成分块唯一ID
     * @param fileId 文档的全局唯一ID（如file_a3f2d4e5...）
     * @param chunkIndex 分块序号（从0开始）
     * @return 格式为"chunk_{fileId}_{chunkIndex}"的chunk_id
     */
    public static String generateChunkId(String fileId, int chunkIndex) {
        // 校验输入
        if (fileId == null || fileId.isEmpty()) {
            throw new IllegalArgumentException("fileId cannot be empty");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }
        // 生成结构化chunk_id
        return String.format("%s_%d", fileId, chunkIndex);
    }



    public static void main(String[] args) {
        // 测试示例
        String path1 = "./docs/report_2024.pdf";
        String path2 = "D:/data/docs/report_2024.pdf";
        
        System.out.println(generateFileId(path1));
        System.out.println(generateFileId(path2));
    }
}
