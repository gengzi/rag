package com.gengzi.model;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gengzi.context.DocumentMetadataMap;
import com.gengzi.context.FileContext;
import com.gengzi.response.LayoutParsingPageItem;
import com.gengzi.response.LayoutParsingResponse;
import com.gengzi.response.ParsingResult;
import com.gengzi.s3.S3ClientUtils;
import com.gengzi.utils.Base64ImageConverter;
import com.gengzi.utils.DivImageReplacer;
import com.gengzi.utils.FileIdGenerator;
import org.apache.hc.core5.http.ContentType;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 版面解析响应结果 → Spring AI Document 转换工具类
 */
@Component
public class LayoutResponseToDocumentConverter {

    @Autowired
    private S3ClientUtils s3ClientUtils;

    public static int[] imgIndex(String imageFileName) {
        // 2. 定义固定前缀和后缀（根据实际文件名调整）
        String prefix = "imgs/img_in_image_box_"; // 坐标前的固定字符串
        String suffix = ".jpg"; // 文件扩展名

        // 3. 截取前缀和后缀之间的坐标字符串（核心步骤）
        // 先找到前缀的结束位置，再找到后缀的开始位置
        int prefixEndIndex = imageFileName.indexOf(prefix) + prefix.length();
        int suffixStartIndex = imageFileName.lastIndexOf(suffix);
        String coordinateStr = imageFileName.substring(prefixEndIndex, suffixStartIndex);
        // 此时 coordinateStr = "81_0_1081_551"

        // 4. 按下划线拆分字符串，得到单个数字的字符串数组
        String[] coordinateStrArr = coordinateStr.split("_");

        // 5. 校验拆分结果（确保是4个数字，避免格式异常）
        if (coordinateStrArr.length != 4) {
//            throw new IllegalArgumentException("文件名格式错误，无法提取4个坐标值：" + imageFileName);
            return new int[4];
        }

        // 6. 转换为整数数组（最终结果）
        int[] coordinates = new int[4];
        for (int i = 0; i < 4; i++) {
            coordinates[i] = Integer.parseInt(coordinateStrArr[i]);
        }
        return coordinates;

    }

    private static boolean bboxEquals(ParsingResult parsingRes, int[] imgIndexs) {
        int[] blockBbox = parsingRes.getBlockBbox();
        for (int i = 0; i < 4; i++) {
            if (!(imgIndexs[i] == blockBbox[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将版面解析响应转换为 Spring AI Document 列表
     * （1个PDF多页 → 多个Document；1个图像 → 1个Document）
     *
     * @param response 版面解析接口响应结果
     * @param context  原始文件信息
     * @return 转换后的 Document 列表（失败场景返回含错误信息的Document）
     */
    public List<Document> convert(LayoutParsingResponse response, FileContext context) {
        List<Document> documentList = new ArrayList<>();

        // 1. 处理响应失败场景：返回含错误信息的Document
        if (response == null || !response.isSuccess()) {
            throw new RuntimeException("版面解析接口调用失败！错误码：" + response.getErrorCode() + "，错误信息：" + response.getErrorMsg());
        }

        // 2. 处理响应成功场景：按页码拆分生成Document（1页1个Document）
        String logId = response.getLogId(); // 接口返回的唯一请求ID（用于Document.id前缀）
        List<LayoutParsingPageItem> pageItems = response.getResult().getLayoutParsingResults();
        // 2.1 构建Document的唯一ID（logId + 页码，确保全局唯一）
        String fileId = FileIdGenerator.generateFileId(context.getKey());
        context.setFileId(fileId);
        for (int i = 0; i < pageItems.size(); i++) {
            LayoutParsingPageItem pageItem = pageItems.get(i);
            int pageNum = i + 1; // 页码（从1开始）


            String filePageId = String.format("%s_%d", fileId, pageNum);
            // 保存图片和解析内容
            saveParseResult(pageItem, fileId, context, pageNum);

//            // 2.2 提取核心文本内容（优先用Markdown.text，无则用prunedResult的JSON字符串）
//            String content = extractContent(pageItem);
////
//            // 2.3 构建元数据（存储页码、文件类型、图像Base64等额外信息）
//            Map<String, Object> metadata = buildMetadata(filePageId, pageNum);


//            // 2.4 创建Document对象并添加到列表
//            EsVectorDocument esVectorDocument = new EsVectorDocument();
//            esVectorDocument.setChunkId(filePageId);
//            esVectorDocument.setDocId(fileId);
//            esVectorDocument.setContent(content);
//            esVectorDocument.setMetadata(metadata);
//            ExtendedDocument document = new ExtendedDocument(esVectorDocument);
//            Document document = new Document(content, metadata);
//            documentList.add(document);
        }

        List<Document> documents = concatenate_markdown_pages(pageItems, context);
        documentList.addAll(documents);

        return documentList;
    }

    private List<Document> concatenate_markdown_pages(List<LayoutParsingPageItem> pageItems, FileContext fileContext) {
        String fileId = fileContext.getFileId();
        String documentId = fileContext.getDocumentId();
        ArrayList<Document> documents = new ArrayList<>();
        HashMap<Integer, Document> pageMetadataMap = new HashMap<>();
        for (int pageNumber = 0; pageNumber < pageItems.size(); pageNumber++) {
            // 获取当前页面md文件信息，判断是否有图片，进行图片内容的替换
            LayoutParsingPageItem layoutParsingPageItem = pageItems.get(pageNumber);
            AtomicReference<String> md = new AtomicReference<>(layoutParsingPageItem.getMarkdown().getText().trim());
            if (StrUtil.isBlankIfStr(md.get())) {
                // 拼接下一页的md文件信息，判断是否有图片，进行图片内容的替换
                DocumentMetadataMap documentMetadataMap = new DocumentMetadataMap(fileContext.getFileName(), documentId, fileContext.getFileId(), ContentType.APPLICATION_PDF.getMimeType(),
                        true, String.valueOf(pageNumber), fileContext.getKbId());
                pageMetadataMap.put(pageNumber, new Document(md.get(), documentMetadataMap.toMap()));
                continue;
            } else {
                layoutParsingPageItem.getMarkdown().getImages().entrySet().forEach(entry -> {
                    String imageKey = entry.getKey();
                    HashMap<String, String> imageContent = new HashMap<>();
                    layoutParsingPageItem.getPrunedResult().getParsingResList().forEach(parsingRes -> {
                        if ("image".equals(parsingRes.getBlockLabel()) && bboxEquals(parsingRes, imgIndex(imageKey))) {
                            String blockContent = parsingRes.getBlockContent();
                            imageContent.put(imageKey, blockContent);
                        }
                    });
                    if (imageContent.size() > 0) {
                        md.set(DivImageReplacer.replaceDivWithImage(md.get(), imageKey, imageContent.get(imageKey)));
                    }
                });
                DocumentMetadataMap currentDocumentMetadataMap = new DocumentMetadataMap(fileContext.getFileName(), documentId, fileContext.getFileId(), ContentType.APPLICATION_PDF.getMimeType(),
                        true, String.valueOf(pageNumber), fileContext.getKbId());

                // 两种截断方式：第一种判断当前页结尾是否end就划分完成
                boolean isEnd = ((pageNumber < pageItems.size() - 1) && layoutParsingPageItem.getMarkdown().getIsEnd());
                // 第二种：不仅判断当前页结尾是否end 还判断下一页开始是否为开始模块
                // boolean isEnd = ((pageNumber < pageItems.size() - 1) && layoutParsingPageItem.getMarkdown().getIsEnd() && pageItems.get(pageNumber + 1).getMarkdown().getIsStart());

                boolean isLastPage = (pageNumber == pageItems.size() - 1);

                if (isEnd || isLastPage) {
                    if (pageMetadataMap.size() > 0) {
                        // 进行合并处理
                        Set<Integer> pageNumbers = pageMetadataMap.keySet();
                        String beforePageNumber = pageNumbers.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
                        currentDocumentMetadataMap.setPageRange(beforePageNumber + "," + pageNumber);
                        ArrayList<String> beforeTexts = new ArrayList<>();
                        pageMetadataMap.keySet().stream().sorted().forEach(num -> {
                            Document document = pageMetadataMap.get(num);
                            beforeTexts.add(document.getText());
                        });
                        String beforeText = beforeTexts.stream().collect(Collectors.joining("\n"));
                        Document document = new Document(beforeText + "\n" + md.get(), currentDocumentMetadataMap.toMap());
                        documents.add(document);
                        pageMetadataMap.clear();
                    } else {
                        Document document = new Document(md.get(), currentDocumentMetadataMap.toMap());
                        documents.add(document);
                    }
                } else {
                    pageMetadataMap.put(pageNumber, new Document(md.get(), currentDocumentMetadataMap.toMap()));
                }
            }
        }
        return documents;
    }

    /**
     * 存储markdown 文件和json文件和img 图片
     */
    private void saveParseResult(LayoutParsingPageItem pageItem, String fileId, FileContext fileContext, int pageNum) {

        String fileKey = String.format("%s/%s_%d", fileId, fileId, pageNum);
        // json文件信息
        if (pageItem.getPrunedResult() != null) {
            String json = null;
            try {
                json = new ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(pageItem.getPrunedResult());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            s3ClientUtils.putObjectByContentBytes(fileContext.getBucketName(), fileKey + ".json", json.getBytes(), "application/json");
        }

        // 优先取Markdown文本（结构化结果，最适合作为Document内容）
        if (pageItem.getMarkdown() != null && pageItem.getMarkdown().getText() != null) {
            String markdown = pageItem.getMarkdown().getText().trim();
            s3ClientUtils.putObjectByContentBytes(fileContext.getBucketName(), fileKey + ".md", markdown.getBytes(), "text/markdown");
        }

        // 存储图片
        if (pageItem.getOutputImages() != null && !pageItem.getOutputImages().isEmpty()) {
            for (Map.Entry<String, String> entry : pageItem.getOutputImages().entrySet()) {
                String imageName = entry.getKey();
                String imageBase64 = entry.getValue();
                s3ClientUtils.putObjectByContentBytes(fileContext.getBucketName(), fileKey + "_" + imageName + ".jpeg", Base64ImageConverter.base64ToBytes(imageBase64), "image/jpeg");
            }
        }

    }

    /**
     * 提取单页的核心文本内容
     * 优先级：Markdown.text → prunedResult（转为JSON字符串）→ "无文本内容"
     */
    private String extractContent(LayoutParsingPageItem pageItem) {
        // 优先取Markdown文本（结构化结果，最适合作为Document内容）
        if (pageItem.getMarkdown() != null && pageItem.getMarkdown().getText() != null) {
            return pageItem.getMarkdown().getText().trim();
        }

        // 若无Markdown，取prunedResult（转为JSON字符串，保留原始解析结果）
        if (pageItem.getPrunedResult() != null) {
            try {
                // 使用Jackson将prunedResult转为JSON字符串（需注入ObjectMapper）
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(pageItem.getPrunedResult());
            } catch (Exception e) {
                return "PrunedResult解析失败：" + e.getMessage();
            }
        }

        // 无任何文本时的默认值
        return "";
    }

    /**
     * 构建单页Document的元数据（存储额外信息，便于后续处理）
     */
    private Map<String, Object> buildMetadata(String fileName, String documentId, String fileId, int pageNum, String kbId) {
        DocumentMetadataMap documentMetadataMap = new DocumentMetadataMap(fileName, documentId, fileId, ContentType.APPLICATION_PDF.getMimeType(), true, String.valueOf(pageNum), kbId);
        documentMetadataMap.setPageRange(String.valueOf(pageNum));
        return documentMetadataMap.toMap();

    }

}