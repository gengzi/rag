package com.gengzi.rag.agent.texttosql.tool;

import cn.hutool.core.io.FileUtil;
import com.gengzi.rag.config.S3Properties;
import com.gengzi.rag.util.S3ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * S3上传工具 - 上传本地文件到S3
 */
@Component
public class S3UploadTool implements Function<S3UploadTool.UploadRequest, String> {

    private static final Logger logger = LoggerFactory.getLogger(S3UploadTool.class);

    @Autowired
    private S3ClientUtils s3ClientUtils;

    @Autowired
    private S3Properties s3Properties;

    /**
     * 上传请求类
     */
    public static class UploadRequest {
        private String localFilePath;
        private String s3Key;

        public UploadRequest() {
        }

        public UploadRequest(String localFilePath, String s3Key) {
            this.localFilePath = localFilePath;
            this.s3Key = s3Key;
        }

        public String getLocalFilePath() {
            return localFilePath;
        }

        public void setLocalFilePath(String localFilePath) {
            this.localFilePath = localFilePath;
        }

        public String getS3Key() {
            return s3Key;
        }

        public void setS3Key(String s3Key) {
            this.s3Key = s3Key;
        }
    }

    @Override
    public String apply(UploadRequest request) {
        String localFilePath = request.getLocalFilePath();
        String s3Key = request.getS3Key();

        try {
            logger.info("开始上传文件到S3: {} -> {}", localFilePath, s3Key);

            Path localPath = Paths.get(localFilePath);
            if (!Files.exists(localPath)) {
                return String.format("错误：本地文件不存在: %s", localFilePath);
            }

            if (!Files.isRegularFile(localPath)) {
                return String.format("错误：路径不是文件: %s", localFilePath);
            }

            // 读取文件内容
            byte[] fileContent = FileUtil.readBytes(localPath.toFile());

            // 上传到S3
            String bucketName = s3Properties.getDefaultBucketName();
            s3ClientUtils.putObjectByContentBytes(bucketName, s3Key, fileContent, "application/octet-stream");

            logger.info("文件上传成功: {} -> {}", localFilePath, s3Key);

            return String.format("成功：文件已上传\nS3路径：%s\n大小：%d 字节",
                    s3Key, fileContent.length);

        } catch (Exception e) {
            logger.error("上传文件到S3失败: {} -> {}", localFilePath, s3Key, e);
            return String.format("错误：上传失败\n详情：%s", e.getMessage());
        }
    }
}
