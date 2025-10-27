package com.gengzi.context;

import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.net.URL;
import java.time.Instant;
import java.util.Map;

public class FileContext {

    private final String eTag;                  // 对象的ETag（哈希校验值）
    private final long contentLength;           // 对象大小（字节数）
    private final String contentType;           // 内容类型（如text/plain）
    private final Instant lastModified;         // 最后修改时间
    private final String versionId;             // 版本ID（若启用版本控制）
    private final String storageClass;          // 存储类别（如STANDARD）
    private final Map<String, String> userMetadata; // 用户自定义元数据
    private final boolean isDeleteMarker;       // 是否为删除标记
    private final String bucketName;            // 所属存储桶
    private final String key;                   // 对象键（路径+文件名）

    // 标识某一个文件的唯一id，通过文件路径得到
    private String fileId;
    private URL fileUrl;
    private String documentId;
    private String kbId;

    private FileContext(String eTag, long contentLength, String contentType,
                        Instant lastModified, String versionId, String storageClass,
                        Map<String, String> userMetadata, boolean isDeleteMarker,
                        String bucketName, String key, String documentId, String kbId) {
        this.eTag = eTag;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.lastModified = lastModified;
        this.versionId = versionId;
        this.storageClass = storageClass;
        this.userMetadata = userMetadata;
        this.isDeleteMarker = isDeleteMarker;
        this.bucketName = bucketName;
        this.key = key;
        this.documentId = documentId;
        this.kbId = kbId;
    }

    /**
     * 从HeadObjectResponse构建元数据对象
     */
    public static FileContext from(HeadObjectResponse headResponse, String bucketName, String key, String documentId, String kbId) {
        return new FileContext(
                headResponse.eTag(),
                headResponse.contentLength(),
                headResponse.contentType(),
                headResponse.lastModified(),
                headResponse.versionId(),
                headResponse.storageClassAsString(),
                headResponse.metadata(),
                headResponse.deleteMarker() == null ? false : true,
                bucketName,
                key,
                documentId,
                kbId
        );
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public URL getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(URL fileUrl) {
        this.fileUrl = fileUrl;
    }

    // Getter方法
    public String getETag() {
        return eTag;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public Map<String, String> getUserMetadata() {
        return userMetadata;
    }

    public boolean isDeleteMarker() {
        return isDeleteMarker;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKey() {
        return key;
    }

    public String getFileNameAndType() {
        if (key.contains("/")) {
            return key.substring(key.lastIndexOf("/") + 1);
        } else {
            return key;
        }
    }

    public String getFileName() {
        String filename = key;
        if (filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf("/") + 1);
        }
        if (filename.contains(".")) {
            return filename.substring(0, filename.lastIndexOf("."));
        }
        return filename;
    }


    @Override
    public String toString() {
        return "S3ObjectMetadata{" +
                "bucketName='" + bucketName + '\'' +
                ", key='" + key + '\'' +
                ", eTag='" + eTag + '\'' +
                ", contentLength=" + contentLength +
                ", contentType='" + contentType + '\'' +
                ", lastModified=" + lastModified +
                ", versionId='" + versionId + '\'' +
                ", storageClass='" + storageClass + '\'' +
                ", isDeleteMarker=" + isDeleteMarker +
                ", fileUrl=" + fileUrl +
                ", documentId='" + documentId + '\'' +
                ", kbId='" + kbId + '\'' +
                '}';
    }


}
