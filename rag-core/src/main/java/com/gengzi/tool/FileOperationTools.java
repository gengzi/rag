package com.gengzi.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 文件操作工具 - 提供文件和目录的基本操作功能
 */
@Component
public class FileOperationTools {

    @Tool(description = "读取文件内容。需要提供完整的文件路径。")
    public String readFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "错误：文件不存在 - " + filePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误：不是一个有效的文件 - " + filePath;
            }
            String content = Files.readString(path);
            return "文件内容:\n" + content;
        } catch (IOException e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "列出指定目录下的所有文件和文件夹")
    public String listDirectory(String directoryPath) {
        try {
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                return "错误：目录不存在 - " + directoryPath;
            }
            if (!directory.isDirectory()) {
                return "错误：不是一个有效的目录 - " + directoryPath;
            }

            File[] files = directory.listFiles();
            if (files == null || files.length == 0) {
                return "目录为空";
            }

            return Arrays.stream(files)
                    .map(file -> String.format("%s %s (%s)",
                            file.isDirectory() ? "[目录]" : "[文件]",
                            file.getName(),
                            file.isDirectory() ? "目录" : formatFileSize(file.length())))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "列出目录失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取文件的详细信息，包括大小、最后修改时间等")
    public String getFileInfo(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "错误：文件不存在 - " + filePath;
            }

            File file = path.toFile();
            StringBuilder info = new StringBuilder();
            info.append("文件信息:\n");
            info.append("名称: ").append(file.getName()).append("\n");
            info.append("路径: ").append(file.getAbsolutePath()).append("\n");
            info.append("类型: ").append(file.isDirectory() ? "目录" : "文件").append("\n");
            if (file.isFile()) {
                info.append("大小: ").append(formatFileSize(file.length())).append("\n");
            }
            info.append("最后修改: ").append(new java.util.Date(file.lastModified())).append("\n");
            info.append("可读: ").append(file.canRead() ? "是" : "否").append("\n");
            info.append("可写: ").append(file.canWrite() ? "是" : "否").append("\n");

            return info.toString();
        } catch (Exception e) {
            return "获取文件信息失败: " + e.getMessage();
        }
    }

    @Tool(description = "检查文件或目录是否存在")
    public String checkFileExists(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return "文件存在: " + filePath + " (" + (file.isDirectory() ? "目录" : "文件") + ")";
        } else {
            return "文件不存在: " + filePath;
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
