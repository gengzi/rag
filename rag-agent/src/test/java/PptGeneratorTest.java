import org.apache.poi.xslf.usermodel.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class PptGeneratorTest {

    /**
     * 测试方法：基于模板生成PPT，复用内容为空但内容但保留版式结构，支持动态扩展页数
     *
     * @param templatePath 模板文件路径（.potx/.pptx）
     * @param outputPath   生成的PPT保存路径
     * @param pageCount    需要生成的总页数
     */
    public static void generateTestPpt(String templatePath, String outputPath, int pageCount) {
        try (
                // 读取模板文件
                FileInputStream fis = new FileInputStream(templatePath);
                XMLSlideShow slideShow = new XMLSlideShow(fis);
                // 输出流：保存生成的PPT
                XMLSlideShow newShow = new XMLSlideShow();
                FileOutputStream fos = new FileOutputStream(outputPath)
        ) {
            // 获取模板中的母版（通常常取第一个个母版，取第一个）
            XSLFSlideMaster master = slideShow.getSlideMasters().get(0);
            // 获取母版包含的所有子版式（布局）
            List<XSLFSlideLayout> layouts = Arrays.stream(master.getSlideLayouts()).toList();
            if (layouts.isEmpty()) {
                throw new RuntimeException("模板中未找到子版式");
            }


            // 打印模板中的子版式信息
            System.out.println("模板中子版式数量：" + layouts.size());

            for (int i = 0; i < layouts.size(); i++) {
                XSLFSlideLayout layout = layouts.get(i);
                System.out.printf("子版式 %d：类型=%s",
                        i, layout.getType());
            }

            // 生成指定页数的PPT（复用子版式，循环使用）
            for (int i = 0; i < pageCount; i++) {
                // 循环使用子版式（例如第1页用第1个版式，第2页用第2个，依次循环）
                XSLFSlideLayout layout = layouts.get(i % layouts.size());

                String name = layout.getName();
                System.out.println("子版名称：" + name);
                XSLFSlide slide1 = newShow.createSlide(layout);
                // 基于子版式创建新幻灯片
                XSLFSlide slide = slideShow.createSlide(layout);

                // 为幻灯片页填充空占位符填充简单文本（为空时可省略，此处仅作演示）
                fillPlaceholders(slide, i + 1);
            }

            // 保存生成的PPT
            slideShow.write(fos);
            System.out.printf("成功生成PPT：%s，共%d页%n", outputPath, pageCount);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 为幻灯片的占位符填充简单文本（可选，为空时可注释）
     *
     * @param slide   幻灯片对象
     * @param pageNum 页码
     */
    private static void fillPlaceholders(XSLFSlide slide, int pageNum) {


        for (XSLFShape shape : slide.getShapes()) {
            String shapeName = shape.getShapeName();
            if ("catalogue_one".equals(shapeName)) {


                XSLFTextShape textBox = (XSLFTextShape) shape;
                String text = textBox.getText(); // 获取文本框内容
                String textBoxName = textBox.getShapeName(); // 获取文本框名称
                System.out.println("找到文本框：" + textBoxName);
                System.out.println("文本框内容：" + (text.isEmpty() ? "（空）" : text));

                textBox.setText("章节1：黄金的价格趋势");


                // 这里可以添加对文本框的操作（如修改内容、获取样式等）

//                if (shape instanceof XSLFGroupShape) {
//                    XSLFGroupShape groupShape = (XSLFGroupShape) shape;
//                    String currentGroupName = groupShape.getShapeName(); // 获取组合的名称
//                    System.out.println("找到组合形状：" + currentGroupName);

//                    // 3. 如果指定了组合名称，只处理匹配的组合
//                    if (groupName != null && !groupName.equals(currentGroupName)) {
//                        continue;
//                    }

                    // 4. 遍历组合内的子形状，寻找文本框
//                    List<XSLFShape> groupChildShapes = groupShape.getShapes();
//                    for (XSLFShape childShape : groupChildShapes) {
//
//                        String shapeName1 = childShape.getShapeName();
//                        if ("章节文本框".equals(shapeName1)) {
//                            if (childShape instanceof XSLFTextShape) {
//                                XSLFTextShape textBox = (XSLFTextShape) childShape;
//                                String text = textBox.getText(); // 获取文本框内容
//                                String textBoxName = textBox.getShapeName(); // 获取文本框名称
//                                System.out.println("找到文本框：" + textBoxName);
//                                System.out.println("文本框内容：" + (text.isEmpty() ? "（空）" : text));
//                                // 这里可以添加对文本框的操作（如修改内容、获取样式等）
//                            }
//                        }
//
////                        // 判断是否为文本框（包括占位符文本框和普通文本框）
////                        if (childShape instanceof XSLFTextShape) {
////                            XSLFTextShape textBox = (XSLFTextShape) childShape;
////                            String text = textBox.getText(); // 获取文本框内容
////                            String textBoxName = textBox.getShapeName(); // 文本框名称
////
////                            System.out.println("组合内找到文本框：" + textBoxName);
////                            System.out.println("文本框内容：" + (text.isEmpty() ? "（空）" : text));
////                            // 这里可以添加对文本框的操作（如修改内容、获取样式等）
////                        }
//                    }
//                }
            }
        }


//        // 填充标题占位符
//        XSLFTextShape titleShape = (XSLFTextShape) slide.getPlaceholder(Placeholder.TITLE);
//        if (titleShape != null) {
//            titleShape.setText("第" + pageNum + "页标题");
//        }
//
//        // 填充内容占位符（目录页/内容页通用）
//        XSLFTextShape contentShape = (XSLFTextShape) slide.getPlaceholder(Placeholder.CONTENT);
//        if (contentShape != null) {
//            // 目录页可添加条目示例（支持向下扩展）
//            contentShape.setText("目录项 1\n目录项 2\n目录项 3\n（可继续添加更多条目）");
//        }
//
//        // 填充副标题占位符（如存在）
//        XSLFTextShape subTitleShape = (XSLFTextShape) slide.getPlaceholder(Placeholder.SUBTITLE);
//        if (subTitleShape != null) {
//            subTitleShape.setText("副标题 " + pageNum);
//        }
    }

    // 测试入口
    public static void main(String[] args) {
        // 模板路径（替换为你的.potx/.pptx文件路径）
        String templatePath = "F:\\baidu\\母版11.potx";
        // 输出路径
        String outputPath = "F:\\baidu\\generated_test.pptx";
        // 需要生成的页数（可自定义，支持超过模板子版式数量）
        int pageCount = 5;
        // 调用生成方法
        generateTestPpt(templatePath, outputPath, pageCount);
    }
}