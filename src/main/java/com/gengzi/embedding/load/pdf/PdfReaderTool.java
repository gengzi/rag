package com.gengzi.embedding.load.pdf;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * pdf文件解析工具
 */
@Component
public class PdfReaderTool {

    private static final Logger logger = LoggerFactory.getLogger(PdfReaderTool.class);
    @Autowired
    private RulesPdfReader rulesPdfReader;

    @Autowired
    private OcrPdfReader ocrPdfReader;

    /**
     * 针对常规 PDF（如纯文本、简单段落）采用pdf规则进行匹配解析
     * 如果解析评估解析比较差，则采用ocr模型进行解析
     * @param filePath 对象存储中文件的路径
     */
    public void pdfReader(String filePath) {
//        PDDocument pdDocument = null;
//        try {
//            pdDocument = rulesPdfReader.readerPdf(filePath);
//            PdfParseQualityEvaluator pdfParseQualityEvaluator = new PdfParseQualityEvaluator(pdDocument);
//            if (!pdfParseQualityEvaluator.evaluate()) {
//                logger.info(pdDocument + ": 规则模型解析结果");
//            } else {
//                ocrPdfReader.pdfParse(filePath);
//            }
//        } catch (Exception e) {
//            // 发生异常，直接ocr识别
//            ocrPdfReader.pdfParse(filePath);
//        } finally {
//            try {
//                pdDocument.close();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }


    }


}
