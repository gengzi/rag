package com.gengzi.enums;

import java.util.Arrays;

/**
 * 完整的S3文件类型枚举类
 * 包含各种常见文件类型及其属性
 */
public enum S3FileType {
    // 文本类型
    TEXT_PLAIN("text/plain", true, false, ".txt"),
    CSV("text/csv", true, false, ".csv"),
    JSON("application/json", true, false, ".json"),
    XML("application/xml", true, false, ".xml"),

    // 图像类型
    JPEG("image/jpeg", false, true, ".jpg", ".jpeg"),
    PNG("image/png", false, true, ".png"),
    GIF("image/gif", false, true, ".gif"),
    SVG("image/svg+xml", true, true, ".svg"),

    // 文档类型
    PDF("application/pdf", false, false, ".pdf"),
    DOC("application/msword", false, false, ".doc"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, false, ".docx"),
    XLS("application/vnd.ms-excel", false, false, ".xls"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false, false, ".xlsx"),

    // 音频类型
    MP3("audio/mpeg", false, false, ".mp3"),
    WAV("audio/wav", false, false, ".wav"),

    // 视频类型
    MP4("video/mp4", false, false, ".mp4"),
    AVI("video/x-msvideo", false, false, ".avi"),

    // 压缩文件
    ZIP("application/zip", false, false, ".zip"),
    GZIP("application/gzip", false, false, ".gz"),

    // 未知类型
    UNKNOWN("application/octet-stream", false, false);

    private final String mimeType;
    private final boolean isTextBased;
    private final boolean isImage;
    private final String[] extensions;

    S3FileType(String mimeType, boolean isTextBased, boolean isImage, String... extensions) {
        this.mimeType = mimeType;
        this.isTextBased = isTextBased;
        this.isImage = isImage;
        this.extensions = extensions != null ? extensions.clone() : new String[0];
    }

    public static S3FileType fromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return UNKNOWN;
        }

        String lowerFileName = fileName.toLowerCase();
        for (S3FileType type : values()) {
            for (String ext : type.extensions) {
                if (lowerFileName.endsWith(ext)) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }

    public static S3FileType fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return UNKNOWN;
        }

        for (S3FileType type : values()) {
            if (type.mimeType.equalsIgnoreCase(mimeType)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String[] getExtensions() {
        return extensions.clone();
    }

    public boolean isTextBased() {
        return isTextBased;
    }

    public boolean isImage() {
        return isImage;
    }

    public boolean isReadableAsString() {
        return isTextBased;
    }

    public boolean supportsImageProcessing() {
        return isImage;
    }

    public boolean hasExtension(String extension) {
        if (extension == null) return false;
        String lowerExt = extension.startsWith(".") ? extension.toLowerCase() : "." + extension.toLowerCase();
        return Arrays.asList(extensions).contains(lowerExt);
    }
}
