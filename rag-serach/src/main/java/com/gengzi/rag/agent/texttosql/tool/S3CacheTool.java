package com.gengzi.rag.agent.texttosql.tool;

import cn.hutool.core.io.FileUtil;
import com.gengzi.rag.config.S3Properties;
import com.gengzi.rag.util.S3ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

/**
 * S3缓存管理工具 - 实现 Function 接口以支持 FunctionToolCallback
 */
@Component
public class S3CacheTool implements Function<S3CacheTool.DownloadRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(S3CacheTool.class);
    private static final String CACHE_ROOT_DIR = System.getProperty("java.io.tmpdir") + File.separator
            + "rag-texttosql-cache";
    private static final int CACHE_TTL_DAYS = 1;
    private static final long MAX_DOWNLOAD_SIZE = 100 * 1024 * 1024;

    @Autowired
    private S3ClientUtils s3ClientUtils;

    @Autowired
    private S3Properties s3Properties;

    /**
     * 下载请求类
     */
    public static class DownloadRequest {
        private String s3Key;

        public DownloadRequest() {
        }

        public DownloadRequest(String s3Key) {
            this.s3Key = s3Key;
        }

        public String getS3Key() {
            return s3Key;
        }

        public void setS3Key(String s3Key) {
            this.s3Key = s3Key;
        }
    }

    /**
     * Function 接口实现 - 从S3下载文件到本地缓存
     */
    @Override
    public String apply(DownloadRequest request) {
        String s3Key = request.getS3Key();
        try {
            logger.info("开始下载S3文件: {}", s3Key);

            // 检查S3文件是否存在
            String bucketName = s3Properties.getDefaultBucketName();
            var headResponse = s3ClientUtils.headObject(bucketName, s3Key);
            if (headResponse == null) {
                return String.format("错误：S3文件不存在: %s", s3Key);
            }

            // 检查文件大小
            Long contentLength = headResponse.contentLength();
            if (contentLength != null && contentLength > MAX_DOWNLOAD_SIZE) {
                return String.format("错误：文件过大 (%d MB)，超过最大限制 (%d MB)",
                        contentLength / (1024 * 1024), MAX_DOWNLOAD_SIZE / (1024 * 1024));
            }

            // 获取缓存文件路径
            Path cachedFilePath = getCachedFilePath(s3Key);

            // 检查缓存是否有效
            if (Files.exists(cachedFilePath) && isCacheValid(cachedFilePath)) {
                logger.info("使用缓存文件: {}", cachedFilePath);
                return String.format("成功：使用缓存文件\n路径：%s\n大小：%d 字节",
                        cachedFilePath.toAbsolutePath(), Files.size(cachedFilePath));
            }

            // 缓存无效或不存在，下载文件
            downloadAndCache(bucketName, s3Key, cachedFilePath);

            long fileSize = Files.size(cachedFilePath);
            logger.info("成功下载并缓存文件: {} ({} 字节)", cachedFilePath, fileSize);

            return String.format("成功：已下载并缓存\n路径：%s\n大小：%d 字节\nS3路径：%s",
                    cachedFilePath.toAbsolutePath(), fileSize, s3Key);

        } catch (Exception e) {
            logger.error("下载S3文件失败: {}", s3Key, e);
            return String.format("错误：下载失败 - %s\n详情：%s", s3Key, e.getMessage());
        }
    }

    /**
     * 获取缓存文件路径
     */
    private Path getCachedFilePath(String s3Key) {
        return Paths.get(CACHE_ROOT_DIR, s3Key);
    }

    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid(Path filePath) {
        try {
            Instant lastModified = Files.getLastModifiedTime(filePath).toInstant();
            Instant expireTime = lastModified.plus(CACHE_TTL_DAYS, ChronoUnit.DAYS);
            return Instant.now().isBefore(expireTime);
        } catch (Exception e) {
            logger.warn("检查缓存有效期失败: {}", filePath, e);
            return false;
        }
    }

    /**
     * 下载并缓存文件
     */
    private void downloadAndCache(String bucketName, String s3Key, Path cachedFilePath) throws Exception {
        // 确保父目录存在
        Files.createDirectories(cachedFilePath.getParent());

        // 如果缓存文件已存在但过期，先删除
        if (Files.exists(cachedFilePath)) {
            Files.delete(cachedFilePath);
            logger.info("删除过期缓存文件: {}", cachedFilePath);
        }

        // 从S3下载文件
        logger.info("从S3下载文件: {} -> {}", s3Key, cachedFilePath);
        byte[] fileContent = s3ClientUtils.getObject(bucketName, s3Key);

        // 写入文件
        FileUtil.writeBytes(fileContent, cachedFilePath.toFile());
        logger.info("文件已缓存: {} ({} 字节)", cachedFilePath, fileContent.length);
    }
}
