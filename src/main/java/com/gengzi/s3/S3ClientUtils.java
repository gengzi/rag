package com.gengzi.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedCopy;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.CopyRequest;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
public class S3ClientUtils {

    private static final Logger logger = LoggerFactory.getLogger(S3ClientUtils.class);
    @Autowired
    @Qualifier("s3Client")
    private S3AsyncClient s3Client;


    @Autowired
    @Qualifier("s3Presigner")
    private S3Presigner presigner;

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            // 空路径表示根目录
            return "";
        }
        return path.endsWith("/") ? path : path + "/";
    }

    public URL generatePresignedUrl(String bucketName, String objectKey) {
        try {
            logger.debug("generatePresignedUrl bucketName:{},objectKey:{}", bucketName, objectKey);
            // 构建获取对象的请求
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            // 构建预签名请求
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getObjectRequest)
                    .signatureDuration(Duration.ofHours(24))
                    .build();
            // 生成预签名URL
            URL url = presigner.presignGetObject(presignRequest).url();
            logger.debug("文件下载地址: " + url.toString());
            return url;
        } catch (Exception e) {
            logger.error("获取下载地址失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public void putObjectByContentBytes(String bucketName, String key, byte[] bytes, String contentType) {
        logger.info("putObjectByContentBytes bucketName:{},key:{}", bucketName, key);
        try (S3TransferManager s3TransferManager = S3TransferManager.builder().s3Client(this.s3Client).build()) {
            CompletableFuture<CompletedUpload> completedUploadCompletableFuture = s3TransferManager.upload(
                    UploadRequest.builder()
                            .putObjectRequest(req -> req
                                    .bucket(bucketName)
                                    .key(key)
                                    .contentType(contentType)) // 根据内容类型调整
                            .requestBody(AsyncRequestBody.fromBytes(bytes))
                            .build()
            ).completionFuture();
            // 会阻塞当前线程，直到异步任务执行完成
            completedUploadCompletableFuture.join();
        }
    }

    public void putObjectByTempFile(String bucketName, String key, Path tempFile,String contentType) {
        logger.info("putObjectByTempFile bucketName:{},key:{}", bucketName, key);
        try (S3TransferManager s3TransferManager = S3TransferManager.builder().s3Client(this.s3Client).build()) {
            CompletableFuture<CompletedUpload> completedUploadCompletableFuture = s3TransferManager.upload(
                    UploadRequest.builder()
                            .putObjectRequest(req -> req
                                    .bucket(bucketName)
                                    .key(key)
                                    .contentType(contentType)) // 根据内容类型调整
                            .requestBody(AsyncRequestBody.fromFile(tempFile))
                            .build()
            ).completionFuture();
            // 会阻塞当前线程，直到异步任务执行完成
            completedUploadCompletableFuture.join();
        }
    }

    public HeadObjectResponse headObject(String bucketName, String key) {
        logger.debug("headObject bucketName:{},key:{} ", bucketName, key);
        try {
            return getObjectAttributes(bucketName, key);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HeadObjectResponse getObjectAttributes(String bucketName, String key) throws IOException {
        try {
            return this.s3Client.headObject(req -> req
                    .bucket(bucketName)
                    .key(key)
            ).get(20, TimeUnit.SECONDS);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (ExecutionException e) {
            String errMsg = String.format("path: %s getFileAttributes error!!! req s3 server :%s",
                    key, e.getCause().toString());
            logger.error(errMsg);
            throw new IOException(errMsg, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (TimeoutException e) {
            throw new IOException("path " + key + "getFileAttributes timeout ", e);
        }
    }

    public byte[] getObject(String bucketName, String key) {
        logger.debug("getObject bucketName:{},key:{}", bucketName, key);
        S3AsyncClient s3AsyncClient = this.s3Client;
        return s3AsyncClient.getObject(
                        builder -> builder
                                .bucket(bucketName)
                                .key(key),
                        AsyncResponseTransformer.toBytes())
                .thenApply(
                        getObjectResponseResponseBytes -> getObjectResponseResponseBytes.asByteArray()
                ).join();
    }

    public void copyObject(String sourceBucketName, String sourceKey, String destinationBucketName, String destinationKey) {
        try (S3TransferManager s3TransferManager = S3TransferManager.builder().s3Client(this.s3Client).build()) {
            CompletableFuture<CompletedCopy> completedCopyCompletableFuture = s3TransferManager.copy(CopyRequest.builder()
                    .copyObjectRequest(CopyObjectRequest.builder()
                            .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                            .sourceBucket(sourceBucketName)
                            .sourceKey(sourceKey)
                            .destinationBucket(destinationBucketName)
                            .destinationKey(destinationKey)
                            .build())
                    .build()).completionFuture();
            completedCopyCompletableFuture.join();
        }
    }

    private ListObjectsV2Publisher getObjectsAttributes(String bucketName, String key) {
        String keyDir = normalizePath(key);
        return this.s3Client.listObjectsV2Paginator(req -> req
                .bucket(bucketName)
                .prefix(keyDir)
                .delimiter("/"));
    }

    /**
     * 获取S3指定桶中某个目录下的所有文件
     *
     * @param bucketName    S3桶名称
     * @param directoryPath 目录路径（例如："documents/reports/"，注意末尾的斜杠）
     * @return 文件列表（包含文件名和相关信息）
     */
    public List<S3Object> listFilesInDirectory(String bucketName, String directoryPath) {
        List<S3Object> s3Objects = new ArrayList<>();
        ListObjectsV2Publisher objectsAttributes = getObjectsAttributes(bucketName, directoryPath);
        SdkPublisher<S3Object> contents = objectsAttributes.contents();
        Flux.from(contents).doOnNext(s3Object -> {
            s3Objects.add(s3Object);
        }).then().block();

        return s3Objects;
    }


    public void putObjectByMultipartFile(String bucketName, String key, MultipartFile file) throws IOException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        // 使用AsyncRequestBody.fromInputStream异步处理文件流
        CompletableFuture<PutObjectResponse> putObjectResponseCompletableFuture = s3Client.putObject(putObjectRequest,
                AsyncRequestBody.fromInputStream(file.getInputStream(), file.getSize(), Executors.newScheduledThreadPool(10)));
        putObjectResponseCompletableFuture.join();
    }


}
