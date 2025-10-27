import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class PdfBoxParser {

    public static void main(String[] args) throws IOException {
        String pdfPath = "F:\\baidu\\大模型应用开发概述- 开营导语.pdf"; // 替换为你的 PDF 文件路径
        String outputDir = "pdf_output/"; // 输出目录

        // 创建输出目录
        new File(outputDir).mkdirs();


        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            // 1. 提取文档元数据
            extractMetadata(document);
            // 2. 提取全文文本
            extractFullText(document, outputDir);

            // 3. 按页面提取文本
            extractPageText(document, outputDir);

            // 4. 提取图片
            extractImages(document, outputDir);

            // 5. 提取指定区域文本（示例：第一页的某个矩形区域）
            if (document.getNumberOfPages() > 0) {
                extractTextByArea(document.getPage(0), outputDir);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 提取 PDF 文档元数据
     */
    private static void extractMetadata(PDDocument document) {
        System.out.println("=== 文档元数据 ===");
        PDDocumentInformation info = document.getDocumentInformation();
        System.out.println("标题: " + (info.getTitle() != null ? info.getTitle() : "无"));
        System.out.println("作者: " + (info.getAuthor() != null ? info.getAuthor() : "无"));
        System.out.println("主题: " + (info.getSubject() != null ? info.getSubject() : "无"));
        System.out.println("关键词: " + (info.getKeywords() != null ? info.getKeywords() : "无"));
        System.out.println("创建时间: " + info.getCreationDate());
        System.out.println("修改时间: " + info.getModificationDate());
        System.out.println("PDF 版本: " + document.getVersion());
        System.out.println("页面数量: " + document.getNumberOfPages());
        System.out.println();
    }

    /**
     * 提取 PDF 全文文本
     */
    private static void extractFullText(PDDocument document, String outputDir) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(document);
        
        // 保存到文件
        File textFile = new File(outputDir + "full_text.txt");
        java.nio.file.Files.write(textFile.toPath(), fullText.getBytes());
        System.out.println("=== 全文提取完成 ===");
        System.out.println("已保存到: " + textFile.getAbsolutePath());
        System.out.println();
    }

    /**
     * 按页面提取文本
     */
    private static void extractPageText(PDDocument document, String outputDir) throws IOException {
        System.out.println("=== 按页面提取文本 ===");
        PDFTextStripper stripper = new PDFTextStripper();
        int totalPages = document.getNumberOfPages();
        
        for (int i = 1; i <= totalPages; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(document);
            
            File pageFile = new File(outputDir + "page_" + i + ".txt");
            java.nio.file.Files.write(pageFile.toPath(), pageText.getBytes());
            System.out.println("已提取第 " + i + " 页到: " + pageFile.getAbsolutePath());
        }
        System.out.println();
    }

    /**
     * 提取 PDF 中的图片
     */
    private static void extractImages(PDDocument document, String outputDir) throws IOException {
        System.out.println("=== 提取图片 ===");
        int imageCount = 0;
        PDPageTree pages = document.getPages();

//        for (int pageNum = 0; pageNum < pages.size(); pageNum++) {
//            PDPage page = pages.get(pageNum);
//            PDResources resources = page.getResources();
//
//            // 遍历页面中的所有图片资源
//            for (org.apache.pdfbox.cos.COSName name : resources.getXObjectNames()) {
//                if (resources.isImageXObject(name)) {
//                    PDImageXObject image = (PDImageXObject) resources.getXObject(name);
//                    BufferedImage bufferedImage = image.getImage();
//
//                    // 保存图片（格式：page_{页码}_image_{序号}.png）
//                    String imagePath = outputDir + "page_" + (pageNum + 1) + "_image_" + (++imageCount) + ".png";
//                    ImageIO.write(bufferedImage, "PNG", new File(imagePath));
//                    System.out.println("已提取图片: " + imagePath);
//                }
//            }
//        }
        System.out.println();
    }

    /**
     * 提取指定区域的文本（示例：提取第一页中 x=100, y=200, 宽=300, 高=200 的矩形区域）
     */
    private static void extractTextByArea(PDPage page, String outputDir) throws IOException {
        System.out.println("=== 提取指定区域文本 ===");
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);
        
        // 定义区域（x, y, 宽度, 高度，单位：点）
        Rectangle2D region = new Rectangle2D.Float(100, 200, 300, 200);
        stripper.addRegion("region1", region);
        stripper.extractRegions(page);
        
        String areaText = stripper.getTextForRegion("region1");
        
        // 保存到文件
        File areaFile = new File(outputDir + "area_text.txt");
        java.nio.file.Files.write(areaFile.toPath(), areaText.getBytes());
        System.out.println("已提取指定区域文本到: " + areaFile.getAbsolutePath());
    }
}
    