package com.gengzi.embedding.load.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PDF解析质量评估器，用于判断基于PdfBox的解析效果是否良好
 * 并决定是否需要切换到视觉模型进行解析
 */
public class PdfParseQualityEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(PdfParseQualityEvaluator.class);
    
    // 乱码特征模式
    private static final Pattern GARBAGE_PATTERN = Pattern.compile("[�ï¿½\\ufffd\\?]+");
    // 连续空白模式
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s{5,}");
    // 中文字符模式
    private static final Pattern CHINESE_CHAR_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");
    // 英文单词模式
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z]{2,}");
    
    // 评估结果
    private EvaluationResult result = new EvaluationResult();

    private PDDocument document;
    public PdfParseQualityEvaluator(PDDocument document) {
        this.document = document;
    }

    /**
     * 评估PDF解析质量
     * @return 评估结果
     */
    public Boolean evaluate() throws IOException {
        // 基本文档信息
        result.pageCount = document.getNumberOfPages();
        // 提取文本并分析
        CustomTextStripper stripper = new CustomTextStripper();
        String content = stripper.getText(document);

        // 分析文本内容
        analyzeTextContent(content);

        // 分析文本块结构
        analyzeTextStructure(stripper.getTextPositions());

        // 综合判断是否需要视觉模型
        determineIfNeedVisionModel();
        logger.info("PdfParseQualityEvaluator: {}", result.toString());
        return result.needVisionModel;
    }
    
    /**
     * 分析文本内容质量
     */
    private void analyzeTextContent(String content) {
        // 检查文本长度
        result.textLength = content.length();
        if (result.textLength < 100) {
            result.issues.add("提取的文本过短，可能不完整");
        }
        
        // 检查空白内容比例
        String strippedContent = content.replaceAll("\\s+", "");
        double whitespaceRatio = 1.0 - (double) strippedContent.length() / Math.max(result.textLength, 1);
        result.whitespaceRatio = whitespaceRatio;
        if (whitespaceRatio > 0.7) {
            result.issues.add("空白内容比例过高: " + String.format("%.2f", whitespaceRatio));
        }
        
        // 检查乱码
        long garbageCount = GARBAGE_PATTERN.matcher(content).results().count();
        result.garbageRatio = (double) garbageCount / Math.max(result.textLength, 1);
        if (result.garbageRatio > 0.05) {
            result.issues.add("发现较多乱码: " + String.format("%.2f", result.garbageRatio));
        }
        
        // 检查有意义内容比例
        long chineseCharCount = CHINESE_CHAR_PATTERN.matcher(content).results().count();
        long englishWordCount = ENGLISH_WORD_PATTERN.matcher(content).results().count();
        result.meaningfulContentRatio = (double)(chineseCharCount + englishWordCount) / Math.max(result.textLength, 1);
        if (result.meaningfulContentRatio < 0.3) {
            result.issues.add("有意义内容比例过低: " + String.format("%.2f", result.meaningfulContentRatio));
        }
    }
    
    /**
     * 分析文本结构合理性
     */
    private void analyzeTextStructure(List<TextPosition> textPositions) {
        if (textPositions.size() < 10) {
            result.issues.add("文本块数量过少，可能解析不完整");
            return;
        }
        
        // 分析文本位置连续性
        int positionAnomalies = 0;
        TextPosition prev = textPositions.get(0);
        
        for (int i = 1; i < textPositions.size(); i++) {
            TextPosition curr = textPositions.get(i);
            
            // 检查垂直方向异常（跳跃过大）
            float yDiff = Math.abs(curr.getY() - prev.getY());
            if (yDiff > 2 * prev.getHeight()) {
                positionAnomalies++;
            }
            
            // 检查水平方向异常
            float xDiff = curr.getX() - (prev.getX() + prev.getWidth());
            if (xDiff > 2 * prev.getWidth() && !isSentenceEnd(prev.getUnicode())) {
                positionAnomalies++;
            }
            
            prev = curr;
        }
        
        result.positionAnomalyRatio = (double) positionAnomalies / textPositions.size();
        if (result.positionAnomalyRatio > 0.1) {
            result.issues.add("文本位置异常较多，可能结构混乱: " + 
                             String.format("%.2f", result.positionAnomalyRatio));
        }
    }
    
    /**
     * 判断是否需要使用视觉模型
     */
    private void determineIfNeedVisionModel() {
        // 以下情况需要切换到视觉模型
        if (result.textLength < 50) {
            result.needVisionModel = true;
            result.reason = "文本内容过短，可能是扫描件或无法提取文本";
        } else if (result.garbageRatio > 0.1) {
            result.needVisionModel = true;
            result.reason = "乱码比例过高，解析效果差";
        } else if (result.meaningfulContentRatio < 0.2) {
            result.needVisionModel = true;
            result.reason = "有意义内容比例过低";
        } else if (result.issues.size() >= 3) {
            result.needVisionModel = true;
            result.reason = "存在多项解析问题，综合质量差";
        } else {
            result.needVisionModel = false;
            result.reason = "解析质量可接受，无需使用视觉模型";
        }
    }
    
    /**
     * 判断是否为句子结尾
     */
    private boolean isSentenceEnd(String text) {
        if (text == null || text.isEmpty()) return false;
        char lastChar = text.charAt(text.length() - 1);
        return lastChar == '.' || lastChar == '。' || lastChar == '!' || 
               lastChar == '！' || lastChar == '?' || lastChar == '？' ||
               lastChar == ';' || lastChar == '；';
    }
    
    /**
     * 自定义文本提取器，用于获取更详细的文本位置信息
     */
    private static class CustomTextStripper extends PDFTextStripper {
        private final List<TextPosition> textPositions = new ArrayList<>();
        
        public CustomTextStripper() throws IOException {
            super();
        }
        
        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            this.textPositions.addAll(textPositions);
            super.writeString(text, textPositions);
        }
        
        public List<TextPosition> getTextPositions() {
            return textPositions;
        }
    }
    
    /**
     * 评估结果类
     */
    public static class EvaluationResult {
        public int pageCount;
        public int textLength;
        public double whitespaceRatio;
        public double garbageRatio;
        public double meaningfulContentRatio;
        public double positionAnomalyRatio;
        public List<String> issues = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
        public boolean needVisionModel;
        public String reason;
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("PDF解析质量评估结果:\n");
            sb.append("页面数量: ").append(pageCount).append("\n");
            sb.append("文本长度: ").append(textLength).append("\n");
            sb.append("空白内容比例: ").append(String.format("%.2f", whitespaceRatio)).append("\n");
            sb.append("乱码比例: ").append(String.format("%.2f", garbageRatio)).append("\n");
            sb.append("有意义内容比例: ").append(String.format("%.2f", meaningfulContentRatio)).append("\n");
            sb.append("位置异常比例: ").append(String.format("%.2f", positionAnomalyRatio)).append("\n");
            
            if (!issues.isEmpty()) {
                sb.append("发现的问题:\n");
                for (String issue : issues) {
                    sb.append("- ").append(issue).append("\n");
                }
            }
            
            if (!errors.isEmpty()) {
                sb.append("错误信息:\n");
                for (String error : errors) {
                    sb.append("- ").append(error).append("\n");
                }
            }
            
            sb.append("是否需要视觉模型: ").append(needVisionModel).append("\n");
            sb.append("原因: ").append(reason).append("\n");
            
            return sb.toString();
        }
    }
    

}
