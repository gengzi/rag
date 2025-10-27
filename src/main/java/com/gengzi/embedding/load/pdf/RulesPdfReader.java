package com.gengzi.embedding.load.pdf;

import com.gengzi.config.S3Properties;
import com.gengzi.s3.S3ClientUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用Apache PdfBox解析PDF文档，提取元数据和内容用于RAG系统
 */
@Component
public class RulesPdfReader {

    // 用于分割段落的正则表达式
    private static final Pattern PARAGRAPH_SPLITTER = Pattern.compile("(?<=[。！？,.!?])\\s+");
    // 用于识别标题的正则表达式（假设标题有较多的空格或特殊格式）
    private static final Pattern TITLE_PATTERN = Pattern.compile("^[\\s\\p{Punct}]*[A-Z0-9\\u4e00-\\u9fa5]{2,}[\\s\\p{Punct}]*$");
    private final String outputDir;
    @Autowired
    private S3Properties s3Properties;
    @Autowired
    private S3ClientUtils s3ClientUtils;

    public RulesPdfReader() {
        this("output");
    }

    public RulesPdfReader(String outputDir) {
        this.outputDir = outputDir;
        // 创建输出目录
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            throw new RuntimeException("无法创建输出目录: " + outputDir, e);
        }
    }


    public PDDocument readerPdf(String pdfFilePath) throws IOException {
        // 下载pdf文件
        byte[] bytes = s3ClientUtils.getObject(s3Properties.getDefaultBucketName(), pdfFilePath);
        PDDocument document = Loader.loadPDF(bytes);
        return document;

    }

    /**
     * 处理PDF文件并生成RAG所需的数据
     *
     * @return RAG文档对象
     */
    public RagDocument processPdf(PDDocument document) {
//
//        // 提取元数据
//        RAGMetadata metadata = extractMetadata(document, pdfFilePath);
//
//        // 提取文本内容并分段
//        List<TextSegment> textSegments = extractAndSegmentText(document);
//
//        // 提取图像（如果需要）
//        extractImages(document, metadata.getDocumentId());
//
//        // 创建RAG文档
//        RagDocument ragDoc = new RagDocument(metadata, textSegments);
//
//        // 保存处理结果（可选）
//        saveRagDocument(ragDoc);
//
//        return ragDoc;
        return null;

    }

    /**
     * 提取PDF元数据
     */
    private RAGMetadata extractMetadata(PDDocument document, String filePath) {
        PDDocumentInformation info = document.getDocumentInformation();
        File pdfFile = new File(filePath);

        RAGMetadata metadata = new RAGMetadata();

        // 生成唯一文档ID（可以使用文件名+哈希或UUID）
        metadata.setDocumentId(UUID.nameUUIDFromBytes(pdfFile.getName().getBytes()).toString());

        // 基本元数据
        metadata.setTitle(Objects.requireNonNullElse(info.getTitle(), pdfFile.getName()));
        metadata.setAuthor(Objects.requireNonNullElse(info.getAuthor(), "未知作者"));
        metadata.setSubject(Objects.requireNonNullElse(info.getSubject(), ""));
        metadata.setKeywords(Objects.requireNonNullElse(info.getKeywords(), ""));
        metadata.setCreator(Objects.requireNonNullElse(info.getCreator(), ""));
        metadata.setProducer(Objects.requireNonNullElse(info.getProducer(), ""));

        // 日期信息
        metadata.setCreationDate(convertDate(info.getCreationDate()));
        metadata.setModificationDate(convertDate(info.getModificationDate()));

        // 文件信息
        metadata.setFileName(pdfFile.getName());
        metadata.setFileSize(pdfFile.length());
        metadata.setPageCount(document.getNumberOfPages());

        // 提取页面尺寸信息
        if (document.getNumberOfPages() > 0) {
            PDPage firstPage = document.getPage(0);
            PDRectangle mediaBox = firstPage.getMediaBox();
            metadata.setPageWidth(mediaBox.getWidth());
            metadata.setPageHeight(mediaBox.getHeight());
        }

        // 处理时间
        metadata.setProcessingDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        return metadata;
    }

    /**
     * 提取文本内容并分段
     */
    private List<TextSegment> extractAndSegmentText(PDDocument document) throws IOException {
        List<TextSegment> segments = new ArrayList<>();
        int pageCount = document.getNumberOfPages();

        for (int pageNum = 0; pageNum < pageCount; pageNum++) {
            // 提取当前页文本
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNum + 1);
            stripper.setEndPage(pageNum + 1);
            String pageText = stripper.getText(document).trim();

            if (pageText.isEmpty()) {
                continue;
            }

            // 将页面文本分段
            String[] paragraphs = PARAGRAPH_SPLITTER.split(pageText);

            for (String para : paragraphs) {
                para = para.trim();
                if (para.isEmpty()) {
                    continue;
                }

                // 判断是否为标题
                boolean isTitle = isTitle(para);

                // 创建文本片段
                TextSegment segment = new TextSegment();
                segment.setDocumentId(""); // 稍后设置
                segment.setPageNumber(pageNum + 1); // 页码从1开始
                segment.setContent(para);
                segment.setLength(para.length());
                segment.setIsTitle(isTitle);
                segment.setPositionInPage(segments.size() + 1);

                // 简单提取关键词（实际应用中可使用更复杂的NLP方法）
                segment.setKeywords(extractKeywords(para, 5));

                segments.add(segment);
            }
        }

        return segments;
    }

    /**
     * 提取图像
     */
    private void extractImages(PDDocument document, String documentId) throws IOException {
        String imageDir = outputDir + File.separator + "images" + File.separator + documentId;
        Files.createDirectories(Paths.get(imageDir));

        int imageIndex = 0;
        int pageCount = document.getNumberOfPages();

        for (int pageNum = 0; pageNum < pageCount; pageNum++) {
            PDPage page = document.getPage(pageNum);
            var resources = page.getResources();

            for (var xObjectName : resources.getXObjectNames()) {
                var xObject = resources.getXObject(xObjectName);

                if (xObject instanceof PDImageXObject) {
                    PDImageXObject image = (PDImageXObject) xObject;
                    String imageFileName = String.format("page_%d_image_%d.%s",
                            pageNum + 1, imageIndex, getImageFormat(image));

                    Path imagePath = Paths.get(imageDir, imageFileName);
//                    Files.write(imagePath, image.getImageAsBytes());

                    imageIndex++;
                }
            }
        }
    }

    /**
     * 保存RAG文档数据（可以保存为JSON或其他格式）
     */
    private void saveRagDocument(RagDocument ragDoc) {
        // 实际应用中可以将数据保存为JSON、存入数据库等
        // 这里仅打印信息表示保存成功
        System.out.printf("已成功处理文档: %s，提取段落数: %d%n",
                ragDoc.getMetadata().getTitle(),
                ragDoc.getSegments().size());
    }

    /**
     * 转换PDF日期格式
     */
    private String convertDate(Calendar calendar) {
        if (calendar == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.getTime());
    }

    /**
     * 判断文本是否为标题
     */
    private boolean isTitle(String text) {
        // 简单判断：较短的文本，可能包含数字或特殊字符
        if (text.length() > 100) { // 标题通常不会太长
            return false;
        }

        Matcher matcher = TITLE_PATTERN.matcher(text);
        return matcher.matches();
    }

    /**
     * 提取关键词（简单实现）
     */
    private List<String> extractKeywords(String text, int maxCount) {
        // 实际应用中可以使用TF-IDF、TextRank等算法
        // 这里使用简单的实现：提取长度大于2的词
        List<String> keywords = new ArrayList<>();
        Pattern pattern = Pattern.compile("[a-zA-Z0-9\\u4e00-\\u9fa5]{2,}");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find() && keywords.size() < maxCount) {
            String word = matcher.group();
            if (!keywords.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * 获取图像格式
     */
    private String getImageFormat(PDImageXObject image) {
        String suffix = image.getSuffix();
        return suffix != null ? suffix : "png";
    }

    /**
     * RAG系统使用的文档对象
     */
    public static class RagDocument {
        private final RAGMetadata metadata;
        private final List<TextSegment> segments;

        public RagDocument(RAGMetadata metadata, List<TextSegment> segments) {
            this.metadata = metadata;
            this.segments = segments;

            // 设置所有片段的文档ID
            String docId = metadata.getDocumentId();
            segments.forEach(segment -> segment.setDocumentId(docId));
        }

        public RAGMetadata getMetadata() {
            return metadata;
        }

        public List<TextSegment> getSegments() {
            return segments;
        }
    }

    /**
     * 文档元数据类
     */
    public static class RAGMetadata {
        private String documentId;
        private String title;
        private String author;
        private String subject;
        private String keywords;
        private String creator;
        private String producer;
        private String creationDate;
        private String modificationDate;
        private String fileName;
        private long fileSize;
        private int pageCount;
        private float pageWidth;
        private float pageHeight;
        private String processingDate;

        // Getters and Setters
        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getKeywords() {
            return keywords;
        }

        public void setKeywords(String keywords) {
            this.keywords = keywords;
        }

        public String getCreator() {
            return creator;
        }

        public void setCreator(String creator) {
            this.creator = creator;
        }

        public String getProducer() {
            return producer;
        }

        public void setProducer(String producer) {
            this.producer = producer;
        }

        public String getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(String creationDate) {
            this.creationDate = creationDate;
        }

        public String getModificationDate() {
            return modificationDate;
        }

        public void setModificationDate(String modificationDate) {
            this.modificationDate = modificationDate;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public int getPageCount() {
            return pageCount;
        }

        public void setPageCount(int pageCount) {
            this.pageCount = pageCount;
        }

        public float getPageWidth() {
            return pageWidth;
        }

        public void setPageWidth(float pageWidth) {
            this.pageWidth = pageWidth;
        }

        public float getPageHeight() {
            return pageHeight;
        }

        public void setPageHeight(float pageHeight) {
            this.pageHeight = pageHeight;
        }

        public String getProcessingDate() {
            return processingDate;
        }

        public void setProcessingDate(String processingDate) {
            this.processingDate = processingDate;
        }
    }

    /**
     * 文本片段类，用于RAG中的检索单元
     */
    public static class TextSegment {
        private String documentId;
        private int pageNumber;
        private String content;
        private int length;
        private boolean isTitle;
        private int positionInPage;
        private List<String> keywords;

        // Getters and Setters
        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public boolean isTitle() {
            return isTitle;
        }

        public void setIsTitle(boolean title) {
            isTitle = title;
        }

        public int getPositionInPage() {
            return positionInPage;
        }

        public void setPositionInPage(int positionInPage) {
            this.positionInPage = positionInPage;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }
    }
}
