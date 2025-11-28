package com.gengzi.rag.agent.myagent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * 文件上传下载工具
 * 支持文件存储到本地 F:/baidu 目录
 * 采用 Spring AI 工具注解，供 LLM 调用
 */
@Component
public class FileTool {

    private static final String BASE_PATH = "F:\\baidu";

    public FileTool() {
        createBaseDirectory();
    }

    /**
     * 创建基础存储目录
     */
    private void createBaseDirectory() {
        try {
            Path basePath = Paths.get(BASE_PATH);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to create base directory: " + BASE_PATH);
        }
    }

    /**
     * 上传文件到本地目录
     *
     * @param fileName 文件名
     * @param content 文件内容 (Base64编码或文本内容)
     * @return 上传结果信息
     */
    @Tool(description = "上传文件到本地存储目录。支持文本内容。")
    public Map<String, Object> uploadFile(
            @ToolParam(description = "文件名，包含扩展名") String fileName,
            @ToolParam(description = "文件内容，文本格式") String content) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 验证参数
            if (fileName == null || fileName.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "文件名不能为空");
                return result;
            }

            if (content == null) {
                result.put("success", false);
                result.put("error", "文件内容不能为空");
                return result;
            }

            // 构建完整文件路径
            Path filePath = Paths.get(BASE_PATH, fileName);

            // 创建父目录
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 写入文件
            Files.write(filePath, content.getBytes("UTF-8"));

            // 获取文件大小
            long fileSize = Files.size(filePath);

            // 构建返回结果
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("filePath", filePath.toString());
            result.put("fileSize", fileSize);
            result.put("message", "文件上传成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "文件上传失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 下载文件内容
     *
     * @param fileName 要下载的文件名
     * @return 文件内容信息
     */
    @Tool(description = "下载指定文件的内容。支持文本文件。")
    public Map<String, Object> downloadFile(
            @ToolParam(description = "要下载的文件名，包含扩展名") String fileName) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 验证参数
            if (fileName == null || fileName.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "文件名不能为空");
                return result;
            }

            // 构建完整文件路径
            Path filePath = Paths.get(BASE_PATH, fileName);

            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                result.put("success", false);
                result.put("error", "文件不存在: " + fileName);
                return result;
            }

            // 检查是否为文件
            if (!Files.isRegularFile(filePath)) {
                result.put("success", false);
                result.put("error", "指定路径不是文件: " + fileName);
                return result;
            }

            // 读取文件内容
            byte[] fileBytes = Files.readAllBytes(filePath);
            String content = new String(fileBytes, "UTF-8");

            // 获取文件信息
            long fileSize = Files.size(filePath);

            // 构建返回结果
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("filePath", filePath.toString());
            result.put("content", content);
            result.put("fileSize", fileSize);
            result.put("message", "文件下载成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "文件下载失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 列出目录中的文件
     *
     * @param directoryPath 目录路径 (相对于基础目录)
     * @return 文件列表信息
     */
    @Tool(description = "列出指定目录中的文件和子目录")
    public Map<String, Object> listFiles(
            @ToolParam(description = "要列出的目录路径，相对于基础目录，留空表示根目录") String directoryPath) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 构建完整目录路径
            Path targetPath;
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                targetPath = Paths.get(BASE_PATH);
            } else {
                targetPath = Paths.get(BASE_PATH, directoryPath);
            }

            // 检查目录是否存在
            if (!Files.exists(targetPath)) {
                result.put("success", false);
                result.put("error", "目录不存在: " + directoryPath);
                return result;
            }

            // 检查是否为目录
            if (!Files.isDirectory(targetPath)) {
                result.put("success", false);
                result.put("error", "指定路径不是目录: " + directoryPath);
                return result;
            }

            // 列出文件和目录
            List<Map<String, Object>> files = new ArrayList<>();
            Files.list(targetPath).forEach(path -> {
                Map<String, Object> fileInfo = new HashMap<>();
                try {
                    fileInfo.put("name", path.getFileName().toString());
                    String relativePath = path.toString().replace(BASE_PATH + "\\", "");
                    if (relativePath.startsWith("\\")) {
                        relativePath = relativePath.substring(1);
                    }
                    fileInfo.put("path", relativePath);
                    fileInfo.put("isDirectory", Files.isDirectory(path));
                    if (!Files.isDirectory(path)) {
                        fileInfo.put("size", Files.size(path));
                    } else {
                        fileInfo.put("size", 0);
                    }
                    fileInfo.put("lastModified", Files.getLastModifiedTime(path).toString());
                    files.add(fileInfo);
                } catch (IOException e) {
                    // 忽略单个文件错误，继续处理其他文件
                    System.err.println("Error processing file: " + path + " - " + e.getMessage());
                }
            });

            // 构建返回结果
            result.put("success", true);
            String relativeDir = targetPath.toString().replace(BASE_PATH + "\\", "");
            if (relativeDir.startsWith("\\")) {
                relativeDir = relativeDir.substring(1);
            }
            result.put("directory", relativeDir);
            result.put("files", files);
            result.put("count", files.size());
            result.put("message", "文件列表获取成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "获取文件列表失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 删除文件
     *
     * @param fileName 要删除的文件名
     * @return 删除结果信息
     */
    @Tool(description = "删除指定的文件")
    public Map<String, Object> deleteFile(
            @ToolParam(description = "要删除的文件名，包含相对路径") String fileName) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 验证参数
            if (fileName == null || fileName.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "文件名不能为空");
                return result;
            }

            // 构建完整文件路径
            Path filePath = Paths.get(BASE_PATH, fileName);

            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                result.put("success", false);
                result.put("error", "文件不存在: " + fileName);
                return result;
            }

            // 删除文件
            Files.delete(filePath);

            // 构建返回结果
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("message", "文件删除成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "文件删除失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 创建目录
     *
     * @param directoryName 要创建的目录名
     * @return 创建结果信息
     */
    @Tool(description = "创建新的子目录")
    public Map<String, Object> createDirectory(
            @ToolParam(description = "要创建的目录名，可以包含路径") String directoryName) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 验证参数
            if (directoryName == null || directoryName.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "目录名不能为空");
                return result;
            }

            // 构建完整目录路径
            Path dirPath = Paths.get(BASE_PATH, directoryName);

            // 创建目录
            Files.createDirectories(dirPath);

            // 构建返回结果
            result.put("success", true);
            result.put("directoryName", directoryName);
            result.put("directoryPath", dirPath.toString());
            result.put("message", "目录创建成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "目录创建失败: " + e.getMessage());
        }

        return result;
    }
}