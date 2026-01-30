package com.gengzi.rag.agent.texttosql.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * 文件存在性检查工具
 */
@Component
public class FileExistsTool implements Function<FileExistsTool.ExistsRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(FileExistsTool.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * 检查请求类
     */
    public static class ExistsRequest {
        private String filePath;

        public ExistsRequest() {
        }

        public ExistsRequest(String filePath) {
            this.filePath = filePath;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    @Override
    public String apply(ExistsRequest request) {
        String filePath = request.getFilePath();
        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                return String.format("文件不存在: %s", filePath);
            }

            if (!Files.isRegularFile(path)) {
                return String.format("路径不是文件: %s", filePath);
            }

            File file = path.toFile();
            return String.format("文件存在\n%s", formatFileInfo(file));

        } catch (Exception e) {
            logger.error("检查文件存在性失败: {}", filePath, e);
            return String.format("错误：检查失败 - %s\n详情：%s", filePath, e.getMessage());
        }
    }

    private String formatFileInfo(File file) {
        try {
            long size = file.length();
            String sizeStr = formatFileSize(size);

            Instant lastModified = Instant.ofEpochMilli(file.lastModified());
            String modifiedStr = DATE_FORMATTER.format(lastModified);

            return String.format("文件名：%s\n  路径：%s\n  大小：%s\n  修改时间：%s",
                    file.getName(),
                    file.getAbsolutePath(),
                    sizeStr,
                    modifiedStr);

        } catch (Exception e) {
            return String.format("文件名：%s\n  路径：%s\n  错误：无法获取文件信息",
                    file.getName(), file.getAbsolutePath());
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
