package com.gengzi.embedding.load.pdf;

import com.gengzi.config.S3Properties;
import com.gengzi.context.FileContext;
import com.gengzi.model.PPStructureV3Tool;
import com.gengzi.response.LayoutParsingResponse;
import com.gengzi.s3.S3ClientUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * pdf 解析方案
 * 简单pdf，基于规则的文本获取和元信息获取
 * 复杂pdf，基于ocr模型的元信息获取
 * 表格图片的pdf，基于大模型的文本获取和元信息，或者基于识别表格的文本获取和元信息
 */
@Component
public class OcrPdfReader {

    private static final Logger logger = LoggerFactory.getLogger(OcrPdfReader.class);

    @Autowired
    private S3Properties s3Properties;

    @Autowired
    private PPStructureV3Tool ppStructureV3Tool;

    @Autowired
    private S3ClientUtils s3ClientUtils;


    public void pdfParse(String filePath, com.gengzi.dao.Document document) {
        // 针对pdf的处理
        // 根据文件地址获取流信息
        // 获取pdf的每一页信息
        // 获取filepath 对应的属性
        String documentId = document.getId();
        String kbId = document.getKbId();
        String defaultBucketName = s3Properties.getDefaultBucketName();
        HeadObjectResponse headObjectResponse = s3ClientUtils.headObject(defaultBucketName, filePath);
        FileContext fileContext = FileContext.from(headObjectResponse, defaultBucketName, filePath, documentId, kbId);
        // 根据每页图片信息调用ocr模型，获取解析后的文本和元信息
        URL url = s3ClientUtils.generatePresignedUrl(defaultBucketName, filePath);
        fileContext.setFileUrl(url);
        logger.info("fileContext:{}", fileContext);

        CompletableFuture<LayoutParsingResponse> future =
                ppStructureV3Tool.asyncParsePdf(fileContext);
        // 将信息入库，根据文本分块规则，将其分块

        // 将分块内容进行embedding，存入es
    }


    /**
     * 将PDF文件的每一页转换为图片
     *
     * @param pdfFilePath PDF文件路径
     * @param dpi         图片分辨率，影响清晰度和文件大小
     */
    public List<Image> convertPdfToImages(String pdfFilePath, int dpi) {
        logger.debug("start pdf convert images...");

        // 加载PDF文档
        try (PDDocument document = Loader.loadPDF(new File(pdfFilePath))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            // 获取PDF总页数
            int pageCount = document.getNumberOfPages();
            logger.debug("Total pages: {}", pageCount);
            ArrayList<Image> images = new ArrayList<>();
            // 遍历每一页并转换为图片
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                // 渲染页面为BufferedImage
                // 参数：页码（从0开始），缩放比例，旋转角度
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(
                        pageIndex, dpi);
                images.add(bufferedImage);
            }
            return images;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public List<Document> getDocsFromPdf() {

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader("classpath:/sample1.pdf",
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build());

        return pdfReader.read();
    }


}
