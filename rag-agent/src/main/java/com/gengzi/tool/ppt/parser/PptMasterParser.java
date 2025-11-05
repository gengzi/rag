package com.gengzi.tool.ppt.parser;

import com.gengzi.config.FileReadService;
import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
import com.gengzi.tool.ppt.model.PptLayout;
import com.gengzi.tool.ppt.model.PptMasterModel;
import com.gengzi.tool.ppt.model.PptPlaceholder;
import org.apache.poi.xslf.usermodel.*;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


/**
 * ppt母版解析类
 */
@Service
public class PptMasterParser {

    @Autowired
    private FileReadService fileReadService;

    /**
     * 解析母版
     *
     * @param pptxPath ppt母版路径   存放到s3存储中
     * @return
     * @throws Exception
     */
    public PptMasterModel parseMaster(String pptxPath) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(fileReadService.readFromClasspath(pptxPath))) {
            PptMasterModel model = new PptMasterModel();
            // 获取母版（可能为多个，限制只支持一个的）
            List<XSLFSlideMaster> masters = ppt.getSlideMasters();
            if (masters == null || masters.size() > 1) {
                throw new IllegalStateException("PPT文件不包含母版信息或存在多个母版信息");
            }
            // 设置模板名称
            model.setMasterName(System.currentTimeMillis() + "");
            // 获取第一个母版信息
            XSLFSlideMaster master = masters.get(0);
            // 解析母版页本身的元素（页脚、logo等）
//            model.setMasterElements(parseMasterElements(master));

            List<PptLayout> layouts = new ArrayList<>();
            Map<String, PptLayout> layoutMap = new HashMap<>();
            // 解析所有子版式
            for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                PptLayout parsedLayout = parseLayout(layout);
                layouts.add(parsedLayout);
                layoutMap.put(parsedLayout.getName(), parsedLayout);
            }

            model.setLayouts(layouts);
            model.setLayoutMap(layoutMap);

            return model;
        }
    }

    /**
     * 解析子版内容
     *
     * @param layout 某一个子版内容
     * @return
     */
    private PptLayout parseLayout(XSLFSlideLayout layout) {
        PptLayout result = new PptLayout();
        // 子版名称 （每个子版样式对用是什么页面）
        result.setName(layout.getName());
        // 根据子版名称识别版式类型
        result.setLayoutType(XSLFSlideLayoutType.fromType(layout.getName()));

        List<PptPlaceholder> placeholders = new ArrayList<>();
        Map<String, PptPlaceholder> placeholderMap = new HashMap<>();
        // 获取所有形状并排序（按z-index）
        List<XSLFShape> shapes = new ArrayList<>(layout.getShapes());
        shapes.sort(Comparator.comparingInt(XSLFShape::getShapeId));

        for (int i = 0; i < shapes.size(); i++) {
            // 每一个子版中的每一个占位符信息
            XSLFShape shape = shapes.get(i);
            // 只过滤占位符元素
            boolean isPlaceholder = shape.isPlaceholder();
            if (!isPlaceholder) {
                continue;
            }

            XmlObject xmlObject = shape.getXmlObject();
            CTNonVisualDrawingProps cNvPr = null;
            if (xmlObject instanceof CTShape ctShape) {
                // 解析替代文本的“标题”（Alt Text Title）
                cNvPr = ctShape.getNvSpPr().getCNvPr();
            }


            // 根据占位符类型进行识别
            // 纯文本、富文本（字体、颜色、段落等） 所有包含文本的形状的基础类，包括文本框、标题、正文等
            if (shape instanceof XSLFTextShape textShape) {
                PptPlaceholder placeholder = parsePlaceholder(cNvPr, textShape, i);
                placeholders.add(placeholder);
                placeholderMap.put(placeholder.getEffectiveName(), placeholder);
            }

            // TODO: 处理其他类型的占位符（图片、图表等）
            if (shape instanceof XSLFPictureShape imgaeShape) {
                PptPlaceholder placeholder = parsePlaceholder(cNvPr, imgaeShape, i);
                placeholders.add(placeholder);
                placeholderMap.put(placeholder.getEffectiveName(), placeholder);
            }

        }

        result.setPlaceholders(placeholders);
        result.setPlaceholderMap(placeholderMap);
        return result;
    }

    private PptPlaceholder parsePlaceholder(CTNonVisualDrawingProps cNvPr, XSLFTextShape textShape, int zIndex) {


        PptPlaceholder placeholder = new PptPlaceholder();
        placeholder.setShapeType(textShape.getShapeType());
        placeholder.setShapeName(textShape.getShapeName());
        placeholder.setDefaultText(textShape.getText());
        placeholder.setBounds(textShape.getAnchor());
        placeholder.setZIndex(zIndex);
        placeholder.setPlaceholder(true);
        // 3. 提取关键属性
        // 备注信息（descr）
        if (cNvPr.isSetDescr()) {
            placeholder.setDescription(cNvPr.getDescr()); // 对应 "此处为整个ppt 的标题"
        }

        // 占位符标题（title）
        if (cNvPr.isSetTitle()) {
            placeholder.setDescriptionTitle(cNvPr.getTitle()); // 对应 "标题"
        }
        return placeholder;
    }

    private PptPlaceholder parsePlaceholder(CTNonVisualDrawingProps cNvPr, XSLFPictureShape imageShape, int zIndex) {
        PptPlaceholder placeholder = new PptPlaceholder();
        placeholder.setShapeType(imageShape.getShapeType());
        placeholder.setShapeName(imageShape.getShapeName());
        placeholder.setBounds(imageShape.getAnchor());
        placeholder.setZIndex(zIndex);
        placeholder.setPlaceholder(true);
        // 3. 提取关键属性
        // 备注信息（descr）
        if (cNvPr.isSetDescr()) {
            placeholder.setDescription(cNvPr.getDescr()); // 对应 "此处为整个ppt 的标题"
        }

        // 占位符标题（title）
        if (cNvPr.isSetTitle()) {
            placeholder.setDescriptionTitle(cNvPr.getTitle()); // 对应 "标题"
        }
        return placeholder;
    }
}