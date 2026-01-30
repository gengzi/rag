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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 文件搜索工具 - 在缓存目录中搜索文件
 */
@Component
public class FileSearchTool implements Function<FileSearchTool.SearchRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(FileSearchTool.class);
    private static final String CACHE_ROOT_DIR = System.getProperty("java.io.tmpdir") + File.separator
            + "rag-texttosql-cache";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * 搜索请求类
     */
    public static class SearchRequest {
        private String fileNamePattern;
        private String extension;
        private Boolean recursive;

        public SearchRequest() {
        }

        public SearchRequest(String fileNamePattern, String extension, Boolean recursive) {
            this.fileNamePattern = fileNamePattern;
            this.extension = extension;
            this.recursive = recursive;
        }

        public String getFileNamePattern() {
            return fileNamePattern;
        }

        public void setFileNamePattern(String fileNamePattern) {
            this.fileNamePattern = fileNamePattern;
        }

        public String getExtension() {
            return extension;
        }

        public void setExtension(String extension) {
            this.extension = extension;
        }

        public Boolean getRecursive() {
            return recursive;
        }

        public void setRecursive(Boolean recursive) {
            this.recursive = recursive;
        }
    }

    @Override
    public String apply(SearchRequest request) {
        try {
            boolean doRecursive = request.recursive == null || request.recursive;

            logger.debug("搜索缓存文件: pattern={}, extension={}, recursive={}",
                    request.fileNamePattern, request.extension, doRecursive);

            Path cacheRoot = Paths.get(CACHE_ROOT_DIR);

            if (!Files.exists(cacheRoot)) {
                return String.format("缓存根目录不存在: %s\n提示：可能还未下载任何文件", CACHE_ROOT_DIR);
            }

            List<File> matchedFiles = new ArrayList<>();
            searchFiles(cacheRoot.toFile(), request.fileNamePattern, request.extension, doRecursive, matchedFiles);

            if (matchedFiles.isEmpty()) {
                StringBuilder msg = new StringBuilder("未找到符合条件的文件\n搜索条件：\n");
                if (request.fileNamePattern != null) {
                    msg.append("  - 文件名模式: ").append(request.fileNamePattern).append("\n");
                }
                if (request.extension != null) {
                    msg.append("  - 扩展名: ").append(request.extension).append("\n");
                }
                msg.append("  - 递归搜索: ").append(doRecursive ? "是" : "否");
                return msg.toString();
            }

            StringBuilder result = new StringBuilder();
            result.append("搜索结果：\n\n");

            for (File file : matchedFiles) {
                result.append(formatFileInfo(file));
                result.append("\n");
            }

            result.append(String.format("\n总计：找到 %d 个匹配文件", matchedFiles.size()));

            return result.toString();

        } catch (Exception e) {
            logger.error("搜索缓存文件失败", e);
            return String.format("错误：搜索失败\n详情：%s", e.getMessage());
        }
    }

    private void searchFiles(File dir, String pattern, String extension, boolean recursive, List<File> results) {
        if (!dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                if (matchesSearch(file, pattern, extension)) {
                    results.add(file);
                }
            } else if (file.isDirectory() && recursive) {
                searchFiles(file, pattern, extension, recursive, results);
            }
        }
    }

    private boolean matchesSearch(File file, String pattern, String extension) {
        String fileName = file.getName();

        if (extension != null && !fileName.toLowerCase().endsWith(extension.toLowerCase())) {
            return false;
        }

        if (pattern != null) {
            String regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".");

            if (!fileName.matches(regex)) {
                return false;
            }
        }

        return true;
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
