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
 * 本地文件检索工具 - 实现 Function 接口以支持 FunctionToolCallback
 */
@Component
public class LocalFileSearchTool implements Function<LocalFileSearchTool.ListRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileSearchTool.class);
    private static final String CACHE_ROOT_DIR = System.getProperty("java.io.tmpdir") + File.separator
            + "rag-texttosql-cache";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * 列表请求类
     */
    public static class ListRequest {
        private String documentId;

        public ListRequest() {
        }

        public ListRequest(String documentId) {
            this.documentId = documentId;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }
    }

    /**
     * Function 接口实现 - 列出指定文档ID的所有缓存文件
     */
    @Override
    public String apply(ListRequest request) {
        String documentId = request.getDocumentId();
        try {
            logger.debug("列出文档缓存文件: {}", documentId);

            Path docCacheDir = Paths.get(CACHE_ROOT_DIR, documentId);

            if (!Files.exists(docCacheDir)) {
                return String.format("缓存目录不存在: %s\n提示：可能还未下载该文档的文件", documentId);
            }

            if (!Files.isDirectory(docCacheDir)) {
                return String.format("错误：路径不是目录: %s", docCacheDir);
            }

            File[] files = docCacheDir.toFile().listFiles();

            if (files == null || files.length == 0) {
                return String.format("缓存目录为空: %s", documentId);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("文档 '%s' 的缓存文件列表：\n", documentId));
            result.append(String.format("缓存目录：%s\n\n", docCacheDir.toAbsolutePath()));

            int fileCount = 0;
            for (File file : files) {
                if (file.isFile()) {
                    fileCount++;
                    result.append(formatFileInfo(file));
                    result.append("\n");
                }
            }

            result.append(String.format("\n总计：%d 个文件", fileCount));

            return result.toString();

        } catch (Exception e) {
            logger.error("列出文档缓存文件失败: {}", documentId, e);
            return String.format("错误：列出文件失败 - %s\n详情：%s", documentId, e.getMessage());
        }
    }

    /**
     * 格式化文件信息
     */
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

    /**
     * 格式化文件大小为人类可读格式
     */
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
