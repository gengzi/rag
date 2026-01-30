package com.gengzi.rag.agent.texttosql.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 缓存清理工具 - 清理过期的缓存文件
 */
@Component
public class CacheCleanupTool implements Function<CacheCleanupTool.CleanupRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(CacheCleanupTool.class);
    private static final String CACHE_ROOT_DIR = System.getProperty("java.io.tmpdir") + File.separator
            + "rag-texttosql-cache";
    private static final int CACHE_TTL_DAYS = 1;

    /**
     * 清理请求类（可以为空，但必须有类定义）
     */
    public static class CleanupRequest {
        // 空请求类，用于符合 Function 接口要求
        public CleanupRequest() {
        }
    }

    @Override
    public String apply(CleanupRequest request) {
        try {
            logger.info("开始清理过期缓存...");

            Path cacheRoot = Paths.get(CACHE_ROOT_DIR);
            if (!Files.exists(cacheRoot)) {
                return "缓存目录不存在，无需清理";
            }

            List<Path> expiredFiles = new ArrayList<>();
            long totalSize = 0;

            // 遍历所有缓存文件
            Files.walk(cacheRoot)
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        try {
                            if (isExpired(filePath)) {
                                expiredFiles.add(filePath);
                            }
                        } catch (Exception e) {
                            logger.warn("检查文件过期状态失败: {}", filePath, e);
                        }
                    });

            // 删除过期文件
            for (Path file : expiredFiles) {
                try {
                    long size = Files.size(file);
                    Files.delete(file);
                    totalSize += size;
                    logger.debug("已删除过期文件: {}", file);
                } catch (Exception e) {
                    logger.warn("删除文件失败: {}", file, e);
                }
            }

            String result = String.format("清理完成\n删除文件数：%d\n释放空间：%.2f MB",
                    expiredFiles.size(),
                    totalSize / (1024.0 * 1024));

            logger.info(result);
            return result;

        } catch (Exception e) {
            logger.error("清理缓存失败", e);
            return String.format("错误：清理失败\n详情：%s", e.getMessage());
        }
    }

    private boolean isExpired(Path filePath) {
        try {
            Instant lastModified = Files.getLastModifiedTime(filePath).toInstant();
            Instant expireTime = lastModified.plus(CACHE_TTL_DAYS, ChronoUnit.DAYS);
            return Instant.now().isAfter(expireTime);
        } catch (Exception e) {
            return false;
        }
    }
}
