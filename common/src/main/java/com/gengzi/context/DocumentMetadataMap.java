package com.gengzi.context;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档元数据映射类，用于存储文档及其分割块的关键信息，支持与Map的双向转换
 * 适用于需要元数据过滤、存储和传输的场景（如RAG检索、文档管理系统）
 */
@Data
public class DocumentMetadataMap {
    // ============================= 字段常量定义 =============================
    /**
     * 原始文档唯一标识（UUID）
     */
    public static final String DOCUMENT_ID = "documentId";
    /**
     * 文档来源路径/标识（如URL、本地文件路径、数据库ID）
     */
    public static final String SOURCE = "source";
    /**
     * 文档格式类型（如pdf、markdown、docx、code_python）
     */
    public static final String CONTENT_TYPE = "contentType";
    /**
     * 文档首次入库时间（UTC格式）
     */
    public static final String CREATED_AT = "createdAt";
    /**
     * 文档业务类别（如合同、技术手册、学术论文）
     */
    public static final String CATEGORY = "category";
    /**
     * 文档主要语言（遵循ISO 639-1标准，如zh-CN、en-US）
     */
    public static final String LANGUAGE = "language";
    /**
     * 文本块唯一标识（格式：documentId_chunk_序号）
     */
    public static final String CHUNK_ID = "chunkId";
    /**
     * 块在原始文档中的序号（从0开始）
     */
    public static final String CHUNK_INDEX = "chunkIndex";
    /**
     * 原始文档分割后的总块数
     */
    public static final String TOTAL_CHUNKS = "totalChunks";
    /**
     * 块内容是否有效（基于内容质量检测结果）
     */
    public static final String IS_VALID = "isValid";
    /**
     * 权限级别（如public、internal、confidential）
     */
    public static final String ACCESS_LEVEL = "accessLevel";
    /**
     * 文档作者/创建人（如用户名、部门名）
     */
    public static final String AUTHOR = "author";
    /**
     * 文档版本（如v1.0、v2.1）
     */
    public static final String VERSION = "version";
    /**
     * 文档核心关键词列表（如["AI", "RAG", "元数据"]）
     */
    public static final String KEYWORDS = "keywords";
    /**
     * 块对应的原始文档页码范围（如5-8、12）
     */
    public static final String PAGE_RANGE = "pageRange";

    /**
     * 文档名称
     */
    public static final String DOCUMENT_NAME = "documentName";

    /**
     * 文件ID
     */
    public static final String FILE_ID = "fileId";

    /**
     * 知识库ID
     */
    public static final String KB_ID = "kbId";

    /**
     * md 解析格式独有的字段，块内容类型，用于拆分chunk时作为属性使用，不放入metadata基础字段中，只作为md格式独有元数据信息
     */
    public static final String CHUNK_CONTENT_TYPE = "chunkContentType";

    /**
     * 原始文档内容
     */
    public static final String ORIGINAL_CONTENT = "originalContent";

    /**
     * 图片资源信息
     */
    public static final String IMAGE_RESOURCE = "image_resource";

    // ============================= 元数据字段 =============================

    /**
     * 知识库id
     */
    private String kbId;

    /**
     * 原始文档唯一标识，自动生成UUID
     * 作用：关联同一文档的所有分割块，支持按文档筛选
     */
    private String documentId;

    /**
     * 文档来源路径或标识
     * 作用：追溯文档来源，支持按来源过滤（如只保留内部系统文档）
     */
    private String source;

    /**
     * 文档格式类型
     * 作用：区分文档格式，支持按格式过滤（如只处理pdf文档）
     */
    private String contentType;

    /**
     * 文档首次入库时间（UTC格式）
     * 作用：支持时间范围过滤（如只保留近30天的文档）
     */
    private String createdAt;

    /**
     * 文档业务类别
     * 作用：按主题领域过滤（如RAG问答时只检索法律合同类文档）
     */
    private String category;

    /**
     * 文档主要语言（ISO 639-1标准）
     * 作用：多语言场景过滤（如只保留中文文档）
     */
    private String language;

    /**
     * 文本块唯一标识（格式：documentId_chunk_序号）
     * 作用：精准定位单个块，支持块级操作（如排除错误块）
     */
    private String chunkId;

    /**
     //     * 块在原始文档中的序号（从0开始）
     //     * 作用：标识块在文档中的位置，支持按位置过滤（如只保留前10块）
     //     */
//    private int chunkIndex;
//
//    /**
//     * 原始文档分割后的总块数
//     * 作用：结合chunkIndex判断块在文档中的相对位置（如前50%的块）
//     */
//    private int totalChunks;

    /**
     * 块内容是否有效（true=有效，false=无效）
     * 作用：过滤低质量块（如OCR识别错误的乱码块、空白块）
     */
    private boolean isValid;

    /**
     * 权限级别（可选）
     * 作用：权限控制过滤（如外部用户只能访问public级文档）
     */
    private String accessLevel;

    /**
     * 文档作者/创建人（可选）
     * 作用：按作者过滤（如只保留技术部撰写的文档）
     */
    private String author;

    /**
     * 文档版本（可选）
     * 作用：版本过滤（如只使用最新版本v3.0的文档）
     */
    private String version;

    /**
     * 核心关键词列表（可选）
     * 作用：按关键词过滤（如只保留包含"数据安全"的文档）
     */
    private List<String> keywords;

    /**
     * 对应原始文档的页码范围（可选）
     * 作用：页码过滤（如只保留PDF的第3-5页内容）
     */
    private String pageRange;


    /**
     * 文档名称
     */
    private String documentName;


    /**
     * 文件ID
     */
    private String fileId;


    /**
     * md 解析格式独有的字段，块内容类型，用于拆分chunk时作为属性使用，不放入metadata基础字段中，只作为md格式独有元数据信息
     */
    private String chunkContentType;

    /**
     * 原始文档内容
     */
    private String originalContent;

    /**
     * 图片资源信息
     */
    private String imageResource;


    // ============================= 构造方法 =============================

    /**
     * 初始化必选字段的构造方法
     *
     * @param contentType 文档格式类型
     * @param isValid     内容是否有效
     */
    public DocumentMetadataMap(String documentName, String documentId, String fileId, String contentType,
                               boolean isValid, String pageRange, String kbId) {
        this.documentName = documentName;
        this.fileId = fileId;
        this.documentId = documentId; //
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME); // 默认为当前时间
        this.contentType = contentType;
        this.isValid = isValid;
        this.pageRange = pageRange;
        this.kbId = kbId;
    }

    // ============================= Map转换方法 =============================

    /**
     * 从Map重建元数据对象（包含必选字段验证）
     *
     * @param metadataMap 包含元数据的Map
     * @return 重建的DocumentMetadataMap对象
     * @throws IllegalArgumentException 当Map缺少必选字段时抛出
     */
    public static DocumentMetadataMap fromMap(Map<String, Object> metadataMap) {
        // 验证必选字段是否存在，确保元数据完整性
        if (!metadataMap.containsKey(FILE_ID) || !metadataMap.containsKey(KB_ID) ||
                !metadataMap.containsKey(DOCUMENT_NAME) || !metadataMap.containsKey(DOCUMENT_ID) ||
                !metadataMap.containsKey(CONTENT_TYPE) || !metadataMap.containsKey(IS_VALID) ||
                !metadataMap.containsKey(PAGE_RANGE)) {
            throw new IllegalArgumentException("Map中缺少必要的元数据字段，请检查是否包含必选字段");
        }

        // 初始化对象（使用必选字段）
        DocumentMetadataMap metadata = new DocumentMetadataMap(
                (String) metadataMap.get(DOCUMENT_NAME),
                (String) metadataMap.get(DOCUMENT_ID),
                (String) metadataMap.get(FILE_ID),
                (String) metadataMap.get(CONTENT_TYPE),
                (Boolean) metadataMap.get(IS_VALID),
                (String) metadataMap.get(PAGE_RANGE),
                (String) metadataMap.get(KB_ID)
        );

        // 覆盖自动生成的字段（如果Map中存在）
        if (metadataMap.containsKey(DOCUMENT_ID)) {
            metadata.documentId = (String) metadataMap.get(DOCUMENT_ID);
        }
        if (metadataMap.containsKey(CHUNK_ID)) {
            metadata.chunkId = (String) metadataMap.get(CHUNK_ID);
        }
        if (metadataMap.containsKey(CREATED_AT)) {
            metadata.createdAt = (String) metadataMap.get(CREATED_AT);
        }
        if (metadataMap.containsKey(CHUNK_CONTENT_TYPE)) {
            metadata.chunkContentType = (String) metadataMap.get(CHUNK_CONTENT_TYPE);
        }
        if (metadataMap.containsKey(ORIGINAL_CONTENT)) {
            metadata.originalContent = (String) metadataMap.get(ORIGINAL_CONTENT);
        }
        if (metadataMap.containsKey(IMAGE_RESOURCE)) {
            metadata.imageResource = (String) metadataMap.get(IMAGE_RESOURCE);
        }

        // 设置可选字段
        metadata.accessLevel = (String) metadataMap.get(ACCESS_LEVEL);
        metadata.author = (String) metadataMap.get(AUTHOR);
        metadata.version = (String) metadataMap.get(VERSION);
        metadata.keywords = (List<String>) metadataMap.get(KEYWORDS);
        metadata.category = (String) metadataMap.get(CATEGORY);
        metadata.language = (String) metadataMap.get(LANGUAGE);
//        metadata.chunkIndex = (Integer) metadataMap.get(CHUNK_INDEX);
//        metadata.totalChunks = (Integer) metadataMap.get(TOTAL_CHUNKS);

        return metadata;
    }

    /**
     * 将元数据转换为Map（可选字段仅在有值时添加）
     *
     * @return 包含元数据的Map对象
     */
    public Map<String, Object> toMap() {
        Map<String, Object> metadataMap = new HashMap<>();

        // 核心必选字段（强制添加）
        metadataMap.put(DOCUMENT_NAME, documentName);
        metadataMap.put(DOCUMENT_ID, documentId);
        metadataMap.put(CONTENT_TYPE, contentType);
        metadataMap.put(CREATED_AT, createdAt);
        metadataMap.put(PAGE_RANGE, pageRange);
        metadataMap.put(IS_VALID, isValid);
        metadataMap.put(FILE_ID, fileId);
        metadataMap.put(KB_ID, kbId);

        if (chunkContentType != null) {
            metadataMap.put(CHUNK_CONTENT_TYPE, chunkContentType);
        }
        if (originalContent != null) {
            metadataMap.put(ORIGINAL_CONTENT, originalContent);
        }
        if (imageResource != null) {
            metadataMap.put(IMAGE_RESOURCE, imageResource);
        }

        if (source != null) {
            metadataMap.put(SOURCE, source);
        }
        if (chunkId != null) {
            metadataMap.put(CHUNK_ID, chunkId);
        }
//        if (chunkIndex != -1) {
//            metadataMap.put(CHUNK_INDEX, chunkIndex);
//        }
//        if (totalChunks != -1) {
//            metadataMap.put(TOTAL_CHUNKS, totalChunks);
//        }
        if (category != null) {
            metadataMap.put(CATEGORY, category);
        }

        if (language != null) {
            metadataMap.put(LANGUAGE, language);
        }

        // 可选字段（仅当有值时添加，避免空值）
        if (accessLevel != null) {
            metadataMap.put(ACCESS_LEVEL, accessLevel);
        }
        if (author != null) {
            metadataMap.put(AUTHOR, author);
        }
        if (version != null) {
            metadataMap.put(VERSION, version);
        }
        if (keywords != null) {
            metadataMap.put(KEYWORDS, keywords);
        }

        return metadataMap;
    }

}
