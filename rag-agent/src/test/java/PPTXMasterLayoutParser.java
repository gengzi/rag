import org.apache.poi.xslf.usermodel.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class PPTXMasterLayoutParser {

    public static void main(String[] args) throws IOException {
        String pptxPath = "F:\\baidu\\母版11.potx"; // 替换为你的PPTX文件路径

        try (FileInputStream fis = new FileInputStream(pptxPath);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            List<XSLFSlideMaster> masters = ppt.getSlideMasters();

            for (int m = 0; m < masters.size(); m++) {
                XSLFSlideMaster master = masters.get(m);
                System.out.println("=== 母版 " + (m + 1) + " ===");

                List<XSLFSlideLayout> layouts = Arrays.stream(master.getSlideLayouts()).toList();
                for (int l = 0; l < layouts.size(); l++) {
                    XSLFSlideLayout layout = layouts.get(l);
                    String layoutName = layout.getName();
                    System.out.println("  版式 " + (l + 1) + ": " + layoutName);

                    // 遍历版式中的所有形状（包括占位符）
                    for (XSLFShape shape : layout.getShapes()) {
                        if (shape instanceof XSLFTextShape) {
                            XSLFTextShape textShape = (XSLFTextShape) shape;
                            String text = textShape.getText();
                            String shapeName = textShape.getShapeName();
                            System.out.println("占位符：" + shapeName);
                            String placeholderType = "非占位符";
                            if (textShape.isPlaceholder()) {

                                placeholderType = "占位符类型: " + textShape.getTextType();
                            }
                            System.out.println("    - 文本形状: \"" + text + "\" (" + placeholderType + ")");
                        } else if (shape instanceof XSLFPictureShape) {
                            System.out.println("    - 图片占位符或图片");
                        } else {
                            System.out.println("    - 其他形状: " + shape.getClass().getSimpleName());
                        }
                    }
                }
            }
        }
    }
}